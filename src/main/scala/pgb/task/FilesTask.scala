package pgb.task

import pgb.{ FilesArtifact, Input, Task }
import pgb.path.Resolver

import java.io.File
import java.net.URI

/** A task that generates zero or more files based on a path. */
object FilesTask extends Task[FilesArtifact] {
  /** @return the file(s) pointed to by the name of this task */
  override def execute(
    name: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[FilesArtifact]
  ): FilesArtifact = {
    val baseUri = new URI(name.getScheme, name.getSchemeSpecificPart, null)
    val path = name.getFragment
    val files = Resolver.resolvePath(path, baseUri)
    FilesArtifact(files)
  }
}
