package pgb

import java.net.{ URI, URISyntaxException }

/** Class to handle manipulating pgb paths.
  *
  * Paths are URI-based; they have a scheme, usually `file:`, and are either relative or absolute.
  *
  * This class has utility methods for turning task names into path URIs.
  */
object BuildPaths {
  /** Resolves the given path, aginst the given root if it is a relative path. This doesn't validate
    * any aspects of the path itself, only the URI formatting.
    * @param path the path to resolve, either relative or absolute
    * @param buildRoot the root to resolve the path against, if it is a relative path
    * @return the absolute, canonical version of the given path, resolved against the given build
    *     root if it was a relative path
    * @throws ConfigException if the URI has invalid syntax or if it's a relative URI with a scheme
    *     set
    */
  def resolvePath(path: String, buildRoot: URI): URI = {
    require(buildRoot.isAbsolute, s"""Build root "$buildRoot" was not absolute""")
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

    buildRoot.resolve(pathUri).normalize
  }
}
