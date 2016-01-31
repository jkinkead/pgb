package pgb

import java.net.URI

/** Contains helper classes for Task. */
object Task {
  /** Represents a task's output type. */
  sealed trait Type
  /** Type for a task producing strings. */
  case object StringType extends Type {
    override def toString(): String = "string"
  }
  /** Type for a task producing files. */
  case object FileType extends Type {
    override def toString(): String = "files"
  }
  /** Type for a task producing no output. */
  case object NoType extends Type {
    override def toString(): String = "(none)"
  }
}

/** A single task in the build system.
  * @tparam O the type of the task's output
  */
trait Task {
  /** Return the name of this task, as it would appear in a build file. */
  def taskName: String

  /** Runs a task with arguments specified in the build file, and the result of the previous run, if
    * it's still present. This will be invoked only after all inputs in all arguments have completed
    * execution.
    * @param name the special name argument for the task
    * @param buildRoot the root of this task's build file
    * @param arguments all non-name arguments for the task
    * @param previousOutput if set, the result of the last run of this task
    * @return the output(s) for this task
    * @throws ConfigException if there was a problem with the task's arguments or name
    * @throws ExecutionException if there was a problem executing the task
    */
  // TODO: buildRoot here was just a hack - this is actually not needed.
  // What IS needed is access to the current build state. Specifically, this needs the task's
  // working directory, and a way to call out to other tasks in the build.
  def execute(
    name: Option[String],
    buildRoot: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Artifact]
  ): Artifact

  /** Return the type of this task. */
  def taskType: Task.Type

  /** Return all of the expected task argument's types. Any arguments of the names returned will be
    * implicitly converted to the type they are paired with.
    * @return the expected names and types of this task's arguments
    */
  def argumentTypes: Map[String, Task.Type] = Map.empty

  /** If this returns false, unexpected arguments (those not in `argumentTypes`) will be considered
    * errors when parsing.
    */
  def allowUnknownArguments: Boolean = false
}

/** Task with file output. */
trait FileOutputTask extends Task {
  final override val taskType: Task.Type = Task.FileType

  // TODO: Consider adding helper execute method here so that subclasses can ignore FilesArtifact.
}

/** Task with string output. */
trait StringOutputTask extends Task {
  final override val taskType: Task.Type = Task.StringType

  // TODO: Consider adding helper execute method here so that subclasses can ignore StringArtifact.
}
