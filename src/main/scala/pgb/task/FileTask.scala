package pgb.task

import pgb.{ ConfigException, FilesArtifact, Input, Task }

import java.io.File

/** A task that generates a single file value whose value is the path equal to by the task name. The
  * file must exist, else the task will fail on execution.
  */
object FileTask extends Task[FilesArtifact] {
  /** @return the file whose path equals the name of this task */
  override def execute(
    name: String,
    arguments: Map[String, Seq[Input]],
    previousOutput: Option[FilesArtifact]
  ): FilesArtifact = {
    val file = new File(name)
    // TODO: Handle look-back paths (those starting with "...").
    if (!file.exists) {
      throw new ConfigException(s"File $name does not exist")
    }
    FilesArtifact(Seq(file))
  }
}
