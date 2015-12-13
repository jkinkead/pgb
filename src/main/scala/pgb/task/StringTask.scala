package pgb.task

import pgb.{ Input, StringArtifact, Task }

/** A task that generates a single string literal value whose value is the task name. */
object StringTask extends Task[StringArtifact] {
  /** @return the name of this task */
  override def execute(
    name: String,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[StringArtifact]
  ): StringArtifact = StringArtifact(name)
}
