package pgb

import java.io.File

/** An output from a task. A task can either output one or more files, or a single string. */
sealed abstract class Output

/** Output wrapping a single string. */
case class StringOutput(value: String) extends Output

/** Output wrapping zero or more files.
  * @param value the file(s) that have been output
  */
case class FileOutput(value: Iterable[File]) extends Output
