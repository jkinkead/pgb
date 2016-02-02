package pgb.task

import pgb.{ Artifact, BuildState, Input, StringArtifact, StringOutputTask }

/** A task that generates a single string literal value whose value is the task name. */
object StringTask extends StringOutputTask {
  override val taskName: String = "string"

  /** @return the name of this task */
  override def execute(
    name: Option[String],
    buildState: BuildState,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Artifact]
  ): Artifact = {
    // TODO: Require name.
    StringArtifact(name.get)
  }
}
