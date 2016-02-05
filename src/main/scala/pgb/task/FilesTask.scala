package pgb.task

import pgb.{ Artifact, BuildState, FileOutputTask, FilesArtifact, StringArtifact }
import pgb.path.Resolver

/** A task that generates zero or more files based on a path. */
object FilesTask extends FileOutputTask {
  override val taskType: String = "files"

  /** @return the file(s) pointed to by the name of this task */
  override def executeValidated(
    name: Option[String],
    stringArguments: Map[String, Seq[StringArtifact]],
    fileArguments: Map[String, FilesArtifact],
    buildState: BuildState
  ): Artifact = {
    // TODO: Require name.
    val files = Resolver.resolvePath(name.get, buildState.buildRoot.toUri)
    FilesArtifact(files)
  }
}
