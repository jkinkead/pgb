package pgb.task

import pgb.{ ConfigException, FileOutputTask, FilesArtifact, Input }
import pgb.path.Resolver

import java.net.URI

/** A task that generates a single file based on a path. */
object FileTask extends FileOutputTask {
  override val taskName: String = "file"

  /** @return the single file pointed to by the name of this task
    * @throws ConfigException if the path doesn't resolve to exactly one file
    */
  override def execute(
    name: Option[String],
    buildRoot: URI,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[FilesArtifact]
  ): FilesArtifact = {
    // TODO: Require name.
    val path = name.get
    val files = Resolver.resolvePath(path, buildRoot)
    if (files.isEmpty) {
      throw new ConfigException(s"""path "$path" resolved to no files.""")
    } else if (files.tail.nonEmpty) {
      throw new ConfigException(s"""path "$path" resolved to multiple files.""")
    }
    FilesArtifact(files)
  }
}
