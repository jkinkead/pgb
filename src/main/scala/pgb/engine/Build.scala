package pgb.engine

import pgb.{ ConfigException, ExecutionException, Task }
import pgb.engine.parser.{ BuildParser, FlatTask, RawTaskArgument, StringArgument, TaskArgument }
import pgb.path.Resolver
import pgb.task._

import java.io.File
import java.net.URI
import java.util.{ Map => JavaMap }
import java.util.concurrent.ConcurrentHashMap

/** Class responsible for managing a pgb build.
  * @param parser the parser to use in parsing build files
  * @param workingDir the working directory of the application, as a URI
  */
class Build(parser: BuildParser, workingDir: URI) {
  /** Mutable task registry. Should only be updated through TaskDef and in this class. */
  private[pgb] val taskRegistry: JavaMap[String, Task[_]] = {
    val registry = new ConcurrentHashMap[String, Task[_]]()
    registry.put("file", FileTask)
    registry.put("files", FilesTask)
    registry.put("string", StringTask)
    registry.put("scalac", SbtScalaTask)
    registry
  }

  /** Strips the fragment portion of a URI, returning the new URI value. Useful for pulling out the
    * build file name from a task URI.
    */
  def stripFragment(uri: URI): URI = {
    new URI(uri.getScheme, uri.getSchemeSpecificPart, null: String)
  }

  /** Validates a reference task. This assume no arguments are allowed, and that `name` is required.
    * @throws ConfigException if the task has extra arguments or is missing a name
    * @return the task's name
    */
  def validateReferenceTask(task: FlatTask): String = {
    if (task.name.isEmpty) {
      task.configException(s""""${task.taskType}" is missing a name""")
    }
    if (task.arguments.nonEmpty) {
      task.configException(s""""${task.taskType}" task takes no arguments""")
    }
    task.name.get
  }

  /** Runs the given targets, first loading the build files they're contained in. */
  def runBuild(targets: Seq[URI]): Unit = {
    // Strip any fragments to generate the build file URIs.
    val buildFileUris: Seq[URI] = (
      targets map { stripFragment }
    )
    val emptyBuild = FlatBuild(Map.empty, Set.empty, Set.empty, Map.empty)
    val flatBuild = buildFileUris.distinct.foldLeft(emptyBuild) { loadBuildFile }

    val buildGraph = targets.foldLeft(new BuildGraph(Map.empty, flatBuild)) {
      case (graph, target) => validateTopLevelTask(target, graph)
    }

    // TODO: Execute the targets!
  }

  /** Loads the given build file into the given build state, returning the new build state. */
  def loadBuildFile(currState: FlatBuild, buildUri: URI): FlatBuild = {
    // TODO: Here, we assume that resolving & parsing the build file is fast. We could check a
    // cache - although that might end up in the `path.Scheme` handling.
    val buildFile = Resolver.resolveSingleFilePath(buildUri.toString, workingDir)
    if (currState.resolvedIncludes.contains(buildFile)) {
      currState
    }

    val allTasks = parser.parseBuildFile(buildFile)

    val (includeTasks, otherTasks) = allTasks partition { _.taskType == "include" }
    val includes = (includeTasks map { task =>
      val validatedIncludePath = validateReferenceTask(task)
      buildUri.resolve(validatedIncludePath)
    }).toSet
    val otherTaskMap = (otherTasks map { task =>
      task.name match {
        case Some(name) => buildUri.resolve("#" + name) -> task
        case None => task.configException("top-level task is missing a name")
      }
    }).toMap
    // Validate that names don't appear twice in the build file.
    if (otherTaskMap.size != otherTasks.size) {
      val tasksByName = otherTasks groupBy { task => buildUri.resolve(task.name.get) }
      val (_, tasks) = (tasksByName find {
        case (_, values) => values.length > 1
      }).get
      // Use the last duplicate definition in the exception message.
      val task = tasks.last
      task.configException(s"""task with name "${task.name.get}" defined multiple times""")
    }

    // Note task defs.
    val taskDefs = (otherTasks filter { _.taskType == "task_def" } map { task =>
      // We already verified that this had a name set.
      task.name.get -> task
    }).toMap

    currState.copy(
      tasks = currState.tasks ++ otherTaskMap,
      resolvedIncludes = currState.resolvedIncludes + buildFile,
      unresolvedIncludes = currState.unresolvedIncludes ++ includes,
      taskDefs = currState.taskDefs ++ taskDefs
    )
  }

  /** Validates a top-level task in a build file, returning the build graph updated to include the
    * task.
    * @param taskUri the task to add to the graph
    * @param buildGraph the build graph constructed so far
    * @return the updated build graph
    */
  def validateTopLevelTask(taskUri: URI, buildGraph: BuildGraph): BuildGraph = {
    val buildFile = stripFragment(taskUri)
    val flatTask = buildGraph.flatBuild.tasks.get(taskUri) getOrElse {
      throw new ConfigException(
        s"""target "${taskUri.getFragment}" not found in build file "${buildFile}""""
      )
    }

    val (taskNode, updatedGraph) = validateFlatTask(flatTask, buildFile, buildGraph)
    updatedGraph.withTask(taskUri, taskNode)
  }

  /** Validates a flat task, returning the build node associated with it, along with the
    * possibly-updated build graph. Note that the build graph will only be updated if the task or
    * one of its dependencies is a task_ref.
    * @param flatTask the task to validate and build a node for
    * @param buildFile the build file this task was found in
    * @param buildGraph the build graph constructed so far
    * @return the build node constructed for the task, paired with the possibly-updated build graph
    */
  def validateFlatTask(
    flatTask: FlatTask,
    buildFile: URI,
    buildGraph: BuildGraph
  ): (BuildNode, BuildGraph) = {
    if (flatTask.taskType == "task_ref") {
      // Fetch the task's URI.
      val referencedTaskUri = buildFile.resolve(validateReferenceTask(flatTask))

      // TODO: If this is a task ref in an unloaded build file:
      //   load that build file

      // Replace this task with the referenced task.
      val updatedGraph = validateTopLevelTask(referencedTaskUri, buildGraph)

      // TODO: This is weird - we're pulling the task back out of the graph, only to add it in the
      // validateTopLevelTask call. Fix?
      (updatedGraph.tasks(referencedTaskUri), updatedGraph)
    } else {
      // Look up the task type. If undefined, run through includes & retry.
      val taskImpl = if (taskRegistry.containsKey(flatTask.taskType)) {
        taskRegistry.get(flatTask.taskType)
      } else {
        // TODO: Run through includes.
        flatTask.configException(s"""unknown task type "${flatTask.taskType}"""")
      }

      // Validate task arguments into new nodes. We also track any updates to the build graph.
      var currGraph = buildGraph
      val arguments: Map[String, Seq[BuildNode]] = flatTask.arguments map {
        case (name, argumentValues) => {
          val expectedTypeOption = taskImpl.argumentTypes.get(name)
          // If the task doesn't allow unknown arguments, and this is an unknown argument, throw an
          // exception.
          if (expectedTypeOption.isEmpty && !taskImpl.allowUnknownArguments) {
            flatTask.configException(s""""${flatTask.taskType}" given unknown argument "$name"""")
          }
          val newValues = argumentValues map {
            case StringArgument(value) => {
              // Implicitly convert barewords to the appropriate type.
              val taskType = expectedTypeOption match {
                case Some(Task.StringType) | None => StringTask
                case Some(Task.FileType) => FileTask
                case Some(Task.NoType) => {
                  // TODO: This should be allowed in a special-case (like, for a "dependsOn" arg).
                  flatTask.configException(s"""argument "$name" has a value with no output""")
                }
              }
              new BuildNode(Some(value), Map.empty, taskType)
            }
            case TaskArgument(value) => {
              val (node, updatedGraph) = validateFlatTask(value, buildFile, currGraph)
              // Validate the task type.
              expectedTypeOption foreach { expectedType =>
                if (node.task.taskType != expectedType) {
                  flatTask.configException(
                    s"""argument "$name" returns ${node.task.taskType}, expected ${expectedType}"""
                  )
                }
              }
              currGraph = updatedGraph
              node
            }
          }
          name -> newValues
        }
      }

      (new BuildNode(flatTask.name, arguments, taskImpl), currGraph)
    }
  }
}
