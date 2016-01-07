package pgb.engine.parser

/** A flat representation of a task parsed out of a file.
  * @param taskType the task type being invoked, e.g. "file"
  * @param name the name of the task. If unset, the name will not appear in `arguments`.
  * @param arguments the arguments to the task
  */
case class FlatTask(taskType: String, name: Option[String], arguments: Map[String, Argument])
  extends FilePositional
