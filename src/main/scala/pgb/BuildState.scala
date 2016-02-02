package pgb

import java.nio.file.Path

/** The build state that an executing task has access to.
  * @param targetDirectory the directory this task should write its output to. This will be deleted
  * completely on a clean, but will remain run-to-run otherwise.
  * @param buildRoot the root of the build. This is the directory the build file for this task is
  * contained in.
  */
case class BuildState(targetDirectory: Path, buildRoot: Path)
