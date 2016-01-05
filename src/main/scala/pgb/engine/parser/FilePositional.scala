package pgb.engine.parser

import scala.util.parsing.input.Positional

/** Trait that stores file info along with file position, and has a helper to generate exception
  * messages.
  */
trait FilePositional extends Positional {
  var filename: String = _
  var contents: String = _

  /** Sets the file info associated with this class.
    * @param filename the name of the file this is in
    * @param contents the file contents as a string
    */
  def setFileInfo(newFilename: String, newContents: String): Unit = {
    filename = newFilename
    contents = newContents
  }

  /** Generates a useful exception message from the stored file data.
    * @param message the base failure message
    * @return an exception message with a pointer to the error line
    */
  def exceptionMessage(baseMessage: String): String = {
    if (filename == null || contents == null) {
      throw new IllegalStateException("exceptionMessage called with file state data unset")
    }
    Util.exceptionMessage(baseMessage, filename, contents, pos)
  }
}
