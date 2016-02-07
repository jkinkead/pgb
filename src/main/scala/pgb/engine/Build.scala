package pgb.engine

import pgb.{ Artifact, BuildState, ConfigException, ExecutionException, Input, Task }
import pgb.engine.parser.{ BuildParser, FlatTask, RawTaskArgument, StringArgument, TaskArgument }
import pgb.path.Resolver
import pgb.task._

import java.io.File
import java.net.URI
import java.nio.file.{ Files, Paths }
import java.util.{ Map => JavaMap }
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._
import scala.collection.mutable

/** Class responsible for managing a pgb build.
  * @param parser the parser to use in parsing build files
  * @param workingDir the working directory of the application, as a URI
  */
class Build(parser: BuildParser, workingDir: URI) {
  /** Mutable task registry. Should only be updated through TaskDef and in this class. */
  private[pgb] val taskRegistry: JavaMap[String, Task] = {
    val registry = new ConcurrentHashMap[String, Task]()
    Seq(FileTask, FilesTask, StringTask, SbtScalaTask) foreach { task =>
      registry.put(task.taskType, task)
    }
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
      case (graph, target) => validateTopLevelTask(target, mutable.LinkedHashSet.empty, graph)
    }

    val results = new ConcurrentHashMap[BuildNode, Artifact]()
    targets foreach { target =>
      // TODO: Handle non-file `target`. This assumes a File.
      val buildRoot = target.resolve(".")
      val targetDirectory = Paths.get(buildRoot.resolve("target/pgb/" + target.getFragment))
      Files.createDirectories(targetDirectory)
      val buildState = BuildState(targetDirectory, Paths.get(buildRoot))
      val plan: Seq[Set[BuildNode]] = executionPlan(buildGraph.tasks(target))
      // Execute each set of items in the plan, in order. Don't repeat items.
      plan foreach { nodes =>
        val unexecutedNodes = nodes -- results.keySet.asScala
        // TODO: Execute in parallel.
        // TODO: Create a working directory here instead. These should be per-task, not per-target.
        unexecutedNodes foreach { node =>
          val taskArguments: Map[String, Seq[Artifact]] = node.arguments mapValues { nodes =>
            nodes map { node =>
              results.get(node)
            }
          }

          // TODO: Look up previous artifact from a cache, or remove it from the `execute`
          // arguments.
          val artifact = node.task.execute(node.name, taskArguments, buildState)
          results.put(node, artifact)
        }
      }
    }
  }

  /** Loads the given build file into the given build state, returning the new build state. */
  def loadBuildFile(currState: FlatBuild, buildUri: URI): FlatBuild = {
    // Here, we assume that resolving & parsing the build file is fast. Parsing should be quick, but
    // the resolution step might be slow. Hopefully, a cache in the Scheme or Resolver will fix
    // this.
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
  def validateTopLevelTask(
    taskUri: URI,
    parentTasks: mutable.LinkedHashSet[URI],
    buildGraph: BuildGraph
  ): BuildGraph = {
    val buildFile = stripFragment(taskUri)
    // Check to see if this task is already in the build.
    if (buildGraph.tasks.contains(taskUri)) {
      // No-op; already processed.
      buildGraph
    } else {
      val flatTask = buildGraph.flatBuild.tasks.get(taskUri) getOrElse {
        throw new ConfigException(
          s"""target "${taskUri.getFragment}" not found in build file "${buildFile}""""
        )
      }

      val (taskNode, updatedGraph) =
        validateFlatTask(flatTask, buildFile, parentTasks + taskUri, buildGraph)
      updatedGraph.withTask(taskUri, taskNode.withId(taskUri))
    }
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
    parentTasks: mutable.LinkedHashSet[URI],
    buildGraph: BuildGraph
  ): (BuildNode, BuildGraph) = {
    if (flatTask.taskType == "task_ref") {
      // Fetch the task's URI.
      // If the task name is relative and has no fragment, treat it as a local task.
      val taskRefName = validateReferenceTask(flatTask)
      val taskRefUri = new URI(taskRefName)
      val referencedTaskUri = if (taskRefUri.getFragment == null) {
        if (!taskRefUri.isAbsolute) {
          // Assume that this is a local task.
          buildFile.resolve("#" + taskRefName)
        } else {
          flatTask.configException(s""""task_ref" URI found with no fragment: $taskRefUri""")
        }
      } else {
        buildFile.resolve(validateReferenceTask(flatTask))
      }

      // TODO: If this is a task ref in an unloaded build file:
      //   load that build file

      // Verify that we aren't creating a circular dependency.
      if (parentTasks.contains(referencedTaskUri)) {
        val trimmedParents = parentTasks dropWhile { _ != referencedTaskUri }
        throw new ConfigException(
          s"circular dependency detected: ${trimmedParents.mkString(" -> ")} -> $referencedTaskUri"
        )
      }

      // Replace this task with the referenced task.
      val updatedGraph = validateTopLevelTask(referencedTaskUri, parentTasks, buildGraph)

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
              val taskImplementation = expectedTypeOption match {
                case Some(Input.Type(_, _, Artifact.StringType)) | None => StringTask
                case Some(Input.Type(_, _, Artifact.FileType)) => FilesTask
                case _ => {
                  flatTask.configException(s"""argument "$name" given task with no output""")
                }
              }
              new BuildNode(None, Some(value), Map.empty, taskImplementation)
            }
            case TaskArgument(value) => {
              val (node, updatedGraph) = validateFlatTask(value, buildFile, parentTasks, currGraph)
              // Validate the task type.
              // TODO: Allow NoType to appear in a dependsOn task.
              expectedTypeOption foreach { inputType =>
                val expectedType = inputType.artifactType
                if (node.task.artifactType != inputType.artifactType) {
                  flatTask.configException(
                    s"""argument "$name" requires """ +
                      s"${inputType.artifactType}, got ${node.task.taskType}"
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

      (new BuildNode(None, flatTask.name, arguments, taskImpl), currGraph)
    }
  }

  /** Build an execution plan for a given target. */
  def executionPlan(target: BuildNode): Seq[Set[BuildNode]] = {
    val childPlan = target.arguments.values.flatten.foldLeft(Seq.empty[Set[BuildNode]]) {
      case (planSoFar, childNode) => {
        val currPlan: Seq[Set[BuildNode]] = executionPlan(childNode)
        // Merge with the plan so far.
        planSoFar.zipAll(currPlan, Set.empty[BuildNode], Set.empty[BuildNode]) map {
          case (soFar, curr) => soFar ++ curr
        }
      }
    }
    childPlan :+ Set(target)
  }
}
