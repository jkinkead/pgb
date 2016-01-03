package pgb.engine.parser

/** A flat representation of a task parsed out of a file.
  * @param taskType the task type being invoked, e.g. "file"
  * @param arguments the arguments to the task, including any task name
  */
case class FlatTask(taskType: String, arguments: Map[String, Argument])
