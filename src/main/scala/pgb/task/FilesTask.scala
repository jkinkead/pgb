package pgb.task

import pgb.{ Artifact, FileOutputTask, FilesArtifact, Input }
import pgb.path.Resolver

import java.net.URI

/** A task that generates zero or more files based on a path. */
object FilesTask extends FileOutputTask {
  override val taskName: String = "files"

  /** @return the file(s) pointed to by the name of this task */
  override def execute(
    name: Option[String],
    buildRoot: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Artifact]
  ): Artifact = {
    // TODO: Require name.
    val files = Resolver.resolvePath(name.get, buildRoot)
    FilesArtifact(files)
  }
}
