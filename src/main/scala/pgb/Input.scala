package pgb

/** An input to a task. These are specified as task arguments in a build file.  All task inputs are
  * the output of another task.
  */
sealed abstract class Input {
  /** @return true if this input has changed during this build execution */
  def isUpdated: Boolean
}

/** An input of files. */
case class FilesInput(files: FilesArtifact, override val isUpdated: Boolean) extends Input

/** An input of a string. */
case class StringInput(value: StringArtifact, override val isUpdated: Boolean) extends Input

/** Holds input types. */
object Input {
  /** Parent class for input types. */
  sealed trait Type
  /** Type for `StringInput`. */
  case object StringType extends Type
  /** Type for `FileInput`. */
  case object FileType extends Type
}
