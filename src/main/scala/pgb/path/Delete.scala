package pgb.path

import java.io.IOException
import java.nio.file.{ FileVisitResult, Files, Path, SimpleFileVisitor }
import java.nio.file.attribute.BasicFileAttributes

/** Class to help with deleting file trees. */
object Delete {
  /** Deletes the file tree rooted at the given directory. */
  def delete(directory: Path): Unit = {
    Files.walkFileTree(directory, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(directory: Path, e: IOException): FileVisitResult = {
        if (e == null) {
          Files.delete(directory)
          FileVisitResult.CONTINUE
        } else {
          // directory iteration failed
          throw e
        }
      }
    })
  }
}
