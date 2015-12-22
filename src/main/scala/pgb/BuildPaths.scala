package pgb

import java.io.File
import java.net.{ URI, URISyntaxException }
import java.nio.file.Files

/** Class to handle manipulating pgb paths.
  *
  * Paths are URI-based; they have a scheme, usually `file:`, and are either relative or absolute.
  *
  * This class has utility methods for turning task names into path URIs, resolving paths into
  * files, and handling glob and lookbehind expansion.
  *
  * TODO: Document paths!
  */
object BuildPaths {
  /** Regex to match a path with a glob. The match starts after the last path separator. */
  val FileGlob = """(?:^|/)[^/]*?(?<=[^\\])[*{\[?]""".r.unanchored

  /** Given a path and a build root, returns the file(s) this path points to.
    * @param path the path to resolve, either relative or absolute
    * @param buildRoot the root to resolve the path against, if it is a relative path
    * @return the absolute, canonical version of the given path, resolved against the given build
    *     root if it was a relative path
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

    // TODO: Factor this into a class.
    val isFilesystem = scheme match {
      case "file" | "github" => true
      case "task" => false
      case _ => throw new ConfigException(s"Unhandled scheme: $scheme")
    }

    if (path.startsWith(".../")) {
      if (!isFilesystem) {
        throw new ConfigException(
          s"""Lookbehind path "$path" specified for non-filesystem scheme "$scheme""""
        )
      }
      // TODO: Loop until we find files.
      null
    } else {
      // TODO: Map build root to a directory, if filesystem. Else, resolve full path.
      if (isFilesystem) {
        // TODO: Map the build file to a base directory.
        val baseDirectory: File = null
      } else {
      }
      null
    }
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

  /** Splits a path into its base component and any lookbehind or wildcard components. For paths
    * with no special components, this will return the path unchanged, and an empty relative path.
    * For paths with special components, the first part will contain the absolute URI of the path
    * leading up to the first special component, and the second part will be a relative URI
    * containing the special components.
    *
    * This assumes that the given path is absolute and non-opaque.
    * @param path the URI of the path to split
    * @return a pair of URIs, the first being the absolute base path, and the second being the
    *     relative wildcard off of that
    * @throws ConfigException if the given path has more than one lookbehind component, or if it has
    *     wildcards preceeding the lookbehind component
    */
  def splitPath(path: URI): (URI, Option[URI]) = {
    // This is everything after the authority (which might be the first path component).
    val pathComponent = path.getRawPath
    val lookbehindIndex = pathComponent.indexOf("/...")
    // Ends with "/..." or contains "/.../".
    if (lookbehindIndex == pathComponent.length - 4 || pathComponent(lookbehindIndex + 4) == '/') {
    }
    null
  }
}
