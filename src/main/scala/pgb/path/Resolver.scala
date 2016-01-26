package pgb.path

import pgb.ConfigException

import java.io.File
import java.net.{ URI, URISyntaxException }
import java.nio.file.{ FileSystem, FileSystems, Files, Paths }

import scala.collection.JavaConverters._

/** Class to handle manipulating pgb paths.
  *
  * Paths are URI-based; they have a scheme, usually `file:`, and are either relative or absolute.
  *
  * This class has utility methods for turning task names into path URIs, resolving paths into
  * files, and handling glob and lookbehind expansion.
  *
  * TODO: Document paths!
  */
object Resolver {
  /** Valid schemes for resolving files. */
  // TODO: Inject as constructor parameter.
  val ValidSchemes = Set("file")

  val LookbehindPrefix = "..." + File.separator

  val filesystem: FileSystem = FileSystems.getDefault

  /** Given a path and a root, returns the file this path points to. This will throw an exception if
    * the provided path doesn't resolve to exactly one file.
    * @param path the path to resolve, either relative or absolute
    * @param buildRoot the root to resolve the path against, if it is a relative path
    * @return the single file this path points to
    * @throws IllegalArgumentException if the build root is a relative path
    * @throws ConfigException if the provided path is invalid, or if it doesn't resolve to exactly
    *     one file
    */
  def resolveSingleFilePath(path: String, buildRoot: URI): File = {
    val files = resolvePath(path, buildRoot)
    if (files.isEmpty) {
      throw new ConfigException(s"""Path "$path" resolved to no files.""")
    } else if (files.tail.nonEmpty) {
      throw new ConfigException(s"""Path "$path" resolved to multiple files.""")
    }

    files.head
  }

  /** Given a path and a build root, returns the file(s) this path points to.
    * @param path the path to resolve, either relative or absolute
    * @param buildRoot the root to resolve the path against, if it is a relative path
    * @return the file(s) this path resolves to
    * @throws IllegalArgumentException if the build root is a relative path
    * @throws ConfigException if the provided path is invalid
    */
  def resolvePath(path: String, buildRoot: URI): Seq[File] = {
    require(buildRoot.isAbsolute, s"""Build root "$buildRoot" was not absolute""")

    // First, figure out if this is a relative path or an absolute path so that we can figure out
    // the URI scheme.

    // The URI version of the literal path provided. May be relative or absolute.
    val pathUri = toPathUri(path)
    val scheme = if (pathUri.isAbsolute) { pathUri.getScheme } else { buildRoot.getScheme }

    if (!ValidSchemes.contains(scheme)) {
      throw new ConfigException(s"Unhandled scheme: $scheme")
    }

    // Map build root to a directory.
    // TODO: Delegate to Scheme implementation.
    val rootDirectory = Paths.get(buildRoot.resolve("."))

    val rawFiles = if (path.startsWith(LookbehindPrefix)) {
      val searchPath = path.stripPrefix(LookbehindPrefix)

      // TODO: Delegate to Scheme implementation?
      var searchRoot = rootDirectory
      var foundFiles = Seq.empty[File]
      // Loop until we find files or hit the empty path.
      while (searchRoot.getRoot != searchRoot && foundFiles.isEmpty) {
        foundFiles = resolvePath(searchPath, searchRoot.toUri)
        searchRoot = searchRoot.resolve("..").normalize
      }
      foundFiles
    } else {
      // TODO: Delegate to Scheme implementation.
      if (pathUri.isAbsolute) {
        // TODO: Should this handle globs?
        Seq(Paths.get(pathUri).toFile)
      } else {
        val paths = GlobHandler.matchPaths(path, rootDirectory) map { _.toFile }
        Seq(paths.toSeq: _*)
      }
    }

    rawFiles filter { _.exists }
  }

  /** Converts a string into a path URI, validating the basic URI formatting.
    * @param path the path to convert, either relative or absolute
    * @return the canonical version of the given path
    * @throws ConfigException if the URI has invalid syntax or if it's a relative URI with a scheme
    *     set
    */
  def toPathUri(path: String): URI = {
    val pathUri = try {
      new URI(path)
    } catch {
      case e: URISyntaxException => {
        throw new ConfigException(s"""Bad URI in path "$path": ${e.getMessage}""")
      }
    }

    if (pathUri.isOpaque) {
      throw new ConfigException(s"""Non-absolute URI with scheme in "$path"""")
    }

    pathUri.normalize
  }
}
