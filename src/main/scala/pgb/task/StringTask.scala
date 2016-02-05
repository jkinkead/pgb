package pgb.task

import pgb.{ Artifact, BuildState, FilesArtifact, StringArtifact, StringOutputTask }

/** A task that generates a single string literal value whose value is the task name. */
object StringTask extends StringOutputTask {
  override val taskType: String = "string"

  /** @return the name of this task */
  override def executeValidated(
    name: Option[String],
    stringArguments: Map[String, Seq[StringArtifact]],
    fileArguments: Map[String, FilesArtifact],
    buildState: BuildState
  ): Artifact = {
    // TODO: Require name.
    StringArtifact(name.get)
  }
}
