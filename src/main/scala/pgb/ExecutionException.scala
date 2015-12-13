package pgb

/** An exception thrown during task execution. This should be thrown when there is a failure while
  * the task is running, such as a compile error.
  */
class ExecutionException(message: String, cause: Exception = null) extends Exception(message, cause)
