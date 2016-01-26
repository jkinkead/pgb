package pgb.engine

import pgb.Task

import java.net.URI

/** A node in the build graph.
  * @param name the name of the task from the build file. Empty if no name was set.
  * @param arguments the arguments to the task, as nodes in the build graph
  * @param task the implementation of the task
  * @param dependencies the dependencies this task has
  */
class BuildNode(
  val name: Option[String],
  val arguments: Map[String, Seq[BuildNode]],
  val task: Task[_]
)
