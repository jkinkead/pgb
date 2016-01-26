package pgb.engine

import pgb.engine.parser.FlatTask

import java.io.File
import java.net.URI

/** Class to hold the state of a build while it's being loaded into a build graph.
  * @param tasks all of the non-`include` tasks in all buildfiles loaded, mapped by their canonical
  *     name
  * @param resolvedIncludes all buildfiles that have been loaded so far
  * @param unresolvedIncludes all `include`d buildfiles that haven't been loaded yet
  * @param taskDefs all `task_def` tasks that have been loaded, mapped from their name
  */
case class FlatBuild(
  tasks: Map[URI, FlatTask],
  resolvedIncludes: Set[File],
  unresolvedIncludes: Set[URI],
  taskDefs: Map[String, FlatTask]
)
