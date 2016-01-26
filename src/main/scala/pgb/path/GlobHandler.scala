package pgb.path

import java.io.IOException
import java.nio.file.{
  AccessDeniedException,
  FileSystems,
  FileVisitResult,
  Files,
  Path,
  PathMatcher,
  Paths,
  SimpleFileVisitor
}
import java.nio.file.attribute.BasicFileAttributes

import scala.collection.mutable

/** Class to walk a directory, finding all files that match a given glob pattern. This will skip
  * directories that can't match, to a certain extent. It's still very non-optimal if a pattern
  * segment contains a `**` alongside other match characters, such as in `src/m**n/Foo.scala`. This
  * is an edge case, though, and likely doesn't need to be efficient.
  */
object GlobHandler {
  // Note that this assumes we won't ever have multiple file systems running in the same build.
  val fileSystem = FileSystems.getDefault

  class Visitor(pattern: String, baseDirectory: Path) extends SimpleFileVisitor[Path] {
    val Wildcard = """(?:^|[^\\])\*\*""".r

    /** Raw patterns to be compiled. */
    val patterns: IndexedSeq[String] = {
      val patternAsPath = Paths.get(pattern)
      1 to patternAsPath.getNameCount map { i =>
        patternAsPath.subpath(0, i).toString
      }
    }

    /** Sequence of patterns to match paths against, rooted at `baseDirectory`. */
    val compiledPatterns: IndexedSeq[PathMatcher] = patterns map { pattern =>
      fileSystem.getPathMatcher("glob:" + pattern)
    }

    /** First index of a pattern that contains a directory-spanning wildcard (`**`), or -1 if no
      * such pattern exists.
      */
    val wildcardIndex: Int = {
      patterns indexWhere { pattern => Wildcard.findFirstIn(pattern).nonEmpty }
    }

    /** Current index into the path segments we're matching. */
    var pathIndex = -1

    /** Accumulator for matched paths. */
    val matchedPaths = mutable.Set.empty[Path]

    /** @return true if we're matching the last pattern segment, and can therefore collate results
      */
    def atEnd: Boolean = pathIndex >= compiledPatterns.length - 1

    /** Checks if the given path matches against our current state. If it does and we're matching
      * the last pattern segment, add it to our `matchedPaths` accumulator.
      * @return true if the path matched
      */
    def checkSegment(path: Path): Boolean = {
      // We can be off the end of the pattern list if we're in the midst of a wildcard match.
      val matcher = compiledPatterns(Math.min(pathIndex, patterns.size - 1))
      // Make sure we match the pattern against the relative root.
      val relativePath = baseDirectory.relativize(path)
      if (matcher.matches(relativePath)) {
        if (atEnd) {
          matchedPaths += path
        }
        true
      } else {
        false
      }
    }

    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      // Only check at the end. This also prevents us from checking with the start index of -1.
      if (atEnd) {
        checkSegment(file)
      }
      FileVisitResult.CONTINUE
    }

    override def visitFileFailed(file: Path, e: IOException): FileVisitResult = {
      e match {
        case _: AccessDeniedException => {
          // Ignore, fine.
          FileVisitResult.CONTINUE
        }
        case _ => throw e
      }
    }

    override def preVisitDirectory(directory: Path, attrs: BasicFileAttributes): FileVisitResult = {
      val atStart = pathIndex == -1
      val dirMatches = !atStart && checkSegment(directory)

      // We want to recurse if we're at the start, or if the directory matches, or if we have a
      // wildcard that *might* match.
      if (atStart || dirMatches || (wildcardIndex >= 0 && pathIndex >= wildcardIndex)) {
        // We're at the start of our traversal, or we match the pattern - recurse!
        pathIndex += 1
        FileVisitResult.CONTINUE
      } else {
        // No match, and no wildcard - skip this directory.
        FileVisitResult.SKIP_SUBTREE
      }
    }

    override def postVisitDirectory(directory: Path, e: IOException): FileVisitResult = {
      pathIndex -= 1
      FileVisitResult.CONTINUE
    }
  }

  /** @return the paths matched by the given pattern, rooted in the given base directory */
  def matchPaths(pattern: String, baseDirectory: Path): Set[Path] = {
    val visitor = new Visitor(pattern, baseDirectory)
    Files.walkFileTree(baseDirectory, visitor)
    Set.empty ++ visitor.matchedPaths
  }
}
