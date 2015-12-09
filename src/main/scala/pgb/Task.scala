package pgb

/** A single task in the build system. */
trait Task {
  /** Runs a task with arguments specified in the build file, and the result of the previous run, if
    * it's still present. This will be invoked only after all inputs in all arguments have completed
    * execution.
    * @param name the special name argument for the task
    * @param arguments all non-name arguments for the task
    * @param previousOutput if set, the result of the last run of this task
    * @return the output(s) for this task
    */
  def execute(
    name: String,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Output]
  ): Output
}
