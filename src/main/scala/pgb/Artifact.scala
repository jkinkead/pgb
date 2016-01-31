package pgb

import java.io.File

/** An artifact produced by a task. These represent the output from a task, and can be used as
  * inputs to other tasks when wrapped by an [Input].
  */
sealed abstract class Artifact {
  /** @return this Artifact wrapped in an Input */
  def asInput(isUpdated: Boolean): Input
  // TODO: Add something here to justify this class - this is where staleness should be implemented.
  // This should produce a timestamp and / or a checksum. Both? Timestamp-based might be the best
  // for a first-pass. However, timestamps should probably be created by the build system, not by
  // the tasks . . .
}

/** A single string artifact. These are frequently literals in build files, but can be other simple
  * outputs.
  * @param value the string value that has been output
  */
case class StringArtifact(value: String) extends Artifact {
  def asInput(isUpdated: Boolean): Input = StringInput(this, isUpdated)
}

/** An artifact for zero or more files.
  * @param value the files that have been output
  */
case class FilesArtifact(values: Iterable[File]) extends Artifact {
  def asInput(isUpdated: Boolean): Input = FilesInput(this, isUpdated)
}

/** A artifact for a task that never produces output. This will always be considered new output. */
case object NoArtifact extends Artifact {
  def asInput(isUpdated: Boolean): Input = {
    throw new IllegalStateException("NoArtifact should not be used as input!")
  }
}
