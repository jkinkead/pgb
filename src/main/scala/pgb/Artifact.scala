package pgb

import java.io.File

/** An artifact produced by a task. These represent the output from a task, and can be used as
  * inputs to other tasks when wrapped by an [Input].
  * @tparam O the type of the artifact
  */
sealed abstract class Artifact[O] {
  /** @return the value of this artifact */
  def value: O

  // TODO: Add something here to justify this class - this is where staleness should be implemented.
}

/** A single string artifact. These are frequently literals in build files, but can be other simple
  * outputs.
  * @param value the string value that has been output
  */
case class StringArtifact(override val value: String) extends Artifact[String]

/** An artifact for a file.
  * @param value the file that has been output
  */
case class FileArtifact(override val value: File) extends Artifact[File]

/** A artifact for a task that never produces output. This will always be considered new output. */
case object NoArtifact extends Artifact[Unit] {
  override def value = Unit
}
