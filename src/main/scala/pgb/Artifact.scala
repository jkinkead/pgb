package pgb

import java.io.File

/** An artifact produced by a task. These represent the output from a task, and can be used as
  * inputs to other tasks when wrapped by an [Input].
  */
sealed abstract class Artifact {
  // TODO: Add something here to justify this class - this is where staleness should be implemented.
  // This should produce a timestamp and / or a checksum. Both? Timestamp-based might be the best
  // for a first-pass. However, timestamps should probably be created by the build system, not by
  // the tasks . . .
}

/** A single string artifact. These are frequently literals in build files, but can be other simple
  * outputs.
  * @param value the string value that has been output
  */
case class StringArtifact(value: String) extends Artifact

/** An artifact for a file.
  * @param value the file that has been output
  */
case class FileArtifact(value: File) extends Artifact

/** A artifact for a task that never produces output. This will always be considered new output. */
case object NoArtifact extends Artifact
