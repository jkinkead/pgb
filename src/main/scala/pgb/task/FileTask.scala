package pgb.task

import pgb.{ Artifact, BuildState, ConfigException, FileOutputTask, FilesArtifact, Input }
import pgb.path.Resolver

/** A task that generates a single file based on a path. */
object FileTask extends FileOutputTask {
  override val taskName: String = "file"

  /** @return the single file pointed to by the name of this task
    * @throws ConfigException if the path doesn't resolve to exactly one file
    */
  override def execute(
    name: Option[String],
    buildState: BuildState,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[Artifact]
  ): Artifact = {
    // TODO: Require name.
    val path = name.get
    val files = Resolver.resolvePath(path, buildState.buildRoot.toUri)
    if (files.isEmpty) {
      throw new ConfigException(s"""path "$path" resolved to no files.""")
    } else if (files.tail.nonEmpty) {
      throw new ConfigException(s"""path "$path" resolved to multiple files.""")
    }
    FilesArtifact(files)
  }
}
