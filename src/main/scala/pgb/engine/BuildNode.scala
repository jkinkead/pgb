package pgb.engine

import pgb.Task

import java.net.URI

/** A node in the build graph.
  * @param id the unique URI of the node, if this is a top-level task
  * @param name the name of the task from the build file. Empty if no name was set.
  * @param arguments the arguments to the task, as nodes in the build graph
  * @param task the implementation of the task
  */
class BuildNode(
    val id: Option[URI],
    val name: Option[String],
    val arguments: Map[String, Seq[BuildNode]],
    val task: Task
) {
  /** @return a copy of this node with ID set */
  def withId(newId: URI): BuildNode = new BuildNode(Some(newId), name, arguments, task)

  /** Consider two build notes equal if they have the same URI. Else, use object identity. */
  override def equals(other: Any): Boolean = {
    other match {
      case buildNode: BuildNode if id.nonEmpty && buildNode.id.nonEmpty => {
        id == buildNode.id
      }
      case otherRef: AnyRef => this eq otherRef
      case _ => false
    }
  }

  /** Consider two build notes equal if they have the same URI. Else, use object identity. */
  override def hashCode(): Int = {
    if (id.nonEmpty) id.hashCode else super.hashCode
  }

  override def toString(): String = {
    if (id.nonEmpty) {
      id.get.toString
    } else {
      s"${task.taskName}(${name.getOrElse("")})"
    }
  }
}
