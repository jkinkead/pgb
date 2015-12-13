package pgb

/** An exception thrown during task execution. This should be thrown when the task arguments or name
  * are obviously wrong, like when a task expecting file input gets a string instead.
  */
class ConfigException(message: String, cause: Exception = null) extends Exception(message, cause)
