package pgb

/** An input to a task. These are specified as task arguments in a build file.  All task inputs are
  * the output of another task.
  * @tparam T the type of this input
  */
sealed abstract class Input[T] {
  /** @return the value of this input */
  def value: T

  /** @return true if this input has changed during this build execution */
  def isUpdated: Boolean
}

/** An input of files. */
case class FileInput(
  override val value: Iterable[FileArtifact],
  override val isUpdated: Boolean
) extends Input[Iterable[FileArtifact]]

/** An input of a string. */
case class StringInput(
  override val value: StringArtifact,
  override val isUpdated: Boolean
) extends Input[StringArtifact]
