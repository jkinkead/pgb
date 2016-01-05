package pgb.engine.parser

import scala.util.parsing.input.Positional

/** A representation of a task parsed out of a file, without any extra processing.
  * @param taskType the task type being invoked, e.g. "file"
  * @param name the name of the task. May be specified later as a named argument.
  * @param arguments the arguments to the task
  */
case class RawTask(taskType: String, name: Option[String], arguments: Seq[Argument])
  extends Positional
