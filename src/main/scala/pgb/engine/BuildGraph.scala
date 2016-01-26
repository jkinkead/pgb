package pgb.engine

import java.net.URI

/** A build graph.
  * @param tasks the validated tasks this graph contains
  * @param flatBuild the raw, flat representation of the build
  */
class BuildGraph(val tasks: Map[URI, BuildNode], val flatBuild: FlatBuild) {
  /** @return a copy of this graph with the given task added */
  def withTask(taskUri: URI, taskNode: BuildNode): BuildGraph = {
    new BuildGraph(tasks + (taskUri -> taskNode), flatBuild)
  }
}
