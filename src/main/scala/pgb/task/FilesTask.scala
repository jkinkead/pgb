package pgb.task

import pgb.{ Artifact, BuildState, FileOutputTask, FilesArtifact, Input }
import pgb.path.Resolver

/** A task that generates zero or more files based on a path. */
object FilesTask extends FileOutputTask {
  override val taskName: String = "files"

  /** @return the file(s) pointed to by the name of this task */
  override def execute(
    name: Option[String],
    buildState: BuildState,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Artifact]
  ): Artifact = {
    // TODO: Require name.
    val files = Resolver.resolvePath(name.get, buildState.buildRoot.toUri)
    FilesArtifact(files)
  }
}
