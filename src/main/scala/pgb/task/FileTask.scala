package pgb.task

import pgb.{ ConfigException, FilesArtifact, Input, Task }
import pgb.path.Resolver

import java.io.File
import java.net.URI

/** A task that generates a single file based on a path. */
object FileTask extends Task[FilesArtifact] {
  /** @return the single file pointed to by the name of this task
    * @throws ConfigException if the path doesn't resolve to exactly one file
    */
  override def execute(
    name: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[FilesArtifact]
  ): FilesArtifact = {
    val baseUri = new URI(name.getScheme, name.getSchemeSpecificPart, null)
    val path = name.getFragment
    val files = Resolver.resolvePath(path, baseUri)
    if (files.isEmpty) {
      throw new ConfigException(s"""path "$path" resolved to no files.""")
    } else if (files.tail.nonEmpty) {
      throw new ConfigException(s"""path "$path" resolved to multiple files.""")
    }
    FilesArtifact(files)
  }
}
