package pgb

/** A reference to a task, for use within task input. A `TaskRef` always points to a `Task` that has
  * completed its run for this build. This has a notion of if the output is new, compared to
  * the previous run, and a way to fetch the current output for the build.
  * @tparam O the output type for this task
  */
trait TaskRef[O] {
  /** @return true if the task produced new output during the current build execution */
  def isUpdated: Boolean

  /** @return the value(s) of this task's execution for the current build */
  def value: O
}
