package pgb.engine.parser

import scala.util.parsing.input.Position

object Util {
  /** Generate a useful exception message from parse data.
    * @param message the base failure message
    * @param filename the name of the file being parsed
    * @param contents the file contents as a string
    * @param position the position in the file of the error
    * @return an exception message with a pointer to the error line
    */
  def exceptionMessage(
    message: String,
    filename: String,
    contents: String,
    position: Position
  ): String = {
    // Basic error message.
    val infoLine = s"$filename:${position.line}: $message"
    // Line of code the error happened at.
    val line = contents.split("\r?\n").take(position.line).last
    // Pointer to the character the error happened at.
    val pointer = " " * (position.column - 1) + '^'

    s"$infoLine\n$line\n$pointer"
  }
}
