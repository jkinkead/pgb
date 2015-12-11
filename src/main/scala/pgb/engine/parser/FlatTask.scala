package pgb.engine.parser

/** A representation of a task parsed out of a file.
  * @param taskType the task type being invoked, e.g. "file"
  * @param name the name of the task. May be specified later as a named argument.
  * @param arguments the arguments to the task
  */
case class FlatTask(taskType: String, name: Option[String], arguments: Seq[Argument])
