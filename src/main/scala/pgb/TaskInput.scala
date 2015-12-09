package pgb

import java.io.File

/** An input to a task. These are specified as task arguments in a build file. */
sealed abstract class Input {
  /** @return true if this input has changed during this build execution */
  def isUpdated: Boolean
}

/** A string input. This is a literal from the build file.
  * @param value the literal value from the build file
  * @param isUpdated if the value in the build file has changed since the last run
  */
case class LiteralInput(value: String, override val isUpdated: Boolean) extends Input

/** An input whose value is the result of another task.
  * @tparam the output type of the wrapped task
  */
abstract class TaskInput[O] extends Input {
  /** @return task a reference to the task this is wrapping */
  def task: TaskRef[O]

  /** @return true if the wrapped task has been updated */
  override def isUpdated = task.isUpdated
}

/** An input for a task producing files. */
case class FileInput(override val task: TaskRef[Iterable[File]]) extends TaskInput[Iterable[File]]

/** An input for a task producing a string. */
case class StringInput(override val task: TaskRef[String]) extends TaskInput[String]
