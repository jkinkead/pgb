package pgb

import java.net.URI

/** A single task in the build system.
  * @tparam O the type of the task's output
  */
trait Task[O <: Artifact] {
  /** Runs a task with arguments specified in the build file, and the result of the previous run, if
    * it's still present. This will be invoked only after all inputs in all arguments have completed
    * execution.
    * @param name the special name argument for the task
    * @param arguments all non-name arguments for the task
    * @param previousOutput if set, the result of the last run of this task
    * @return the output(s) for this task
    * @throws ConfigException if there was a problem with the task's arguments or name
    * @throws ExecutionException if there was a problem executing the task
    */
  def execute(
    name: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[O]
  ): O
}
