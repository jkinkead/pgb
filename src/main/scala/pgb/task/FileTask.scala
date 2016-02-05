package pgb.task

import pgb.{ Artifact, BuildState, ConfigException, FileOutputTask, FilesArtifact, StringArtifact }
import pgb.path.Resolver

/** A task that generates a single file based on a path. */
object FileTask extends FileOutputTask {
  override val taskType: String = "file"

  /** @return the single file pointed to by the name of this task
    * @throws ConfigException if the path doesn't resolve to exactly one file
    */
  override def executeValidated(
    name: Option[String],
    stringArguments: Map[String, Seq[StringArtifact]],
    fileArguments: Map[String, FilesArtifact],
    buildState: BuildState
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
