package pgb

import java.net.URI

class BuildPathsSpec extends UnitSpec {
  val buildRoot = new URI("file:///home/jkinkead/build.pgb")

  "resolvePath" should "resolve a relative file path" in {
    val result = BuildPaths.resolvePath("src/foo.scala", buildRoot)
    result shouldBe new URI("file:///home/jkinkead/src/foo.scala")
  }

  it should "resolve paths with special characters" in {
    val result = BuildPaths.resolvePath(".../src/**/foo*.scala", buildRoot)
    result shouldBe new URI("file:///home/jkinkead/.../src/**/foo*.scala")
  }

  it should "fail on opaque paths" in {
    val exception = intercept[ConfigException] {
      BuildPaths.resolvePath("file:src/foo.scala", buildRoot)
    }
    exception.getMessage should startWith("Non-absolute URI")
  }

  it should "resolve an absolute file path" in {
    val path = "file:///home/jkinkead/other/src/foo.scala"
    val result = BuildPaths.resolvePath(path, buildRoot)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "resolve an absolute github path" in {
    val path = "github:///jkinkead/project/foo.pgb#task"
    val result = BuildPaths.resolvePath(path, buildRoot)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "canonicalize an absolute path" in {
    val result =
      BuildPaths.resolvePath("file:///jkinkead/other/src/main/../test/foo.scala", buildRoot)
    // Result should be an unmodified path.
    result shouldBe new URI("file:///jkinkead/other/src/test/foo.scala")
  }

  it should "canonicalize a relative path" in {
    val result = BuildPaths.resolvePath("test/../main/foo.scala", buildRoot)
    // Result should be an unmodified path.
    result shouldBe new URI("file:///home/jkinkead/main/foo.scala")
  }

  it should "throw an exception for a badly-formatted URI" in {
    val exception = intercept[ConfigException] {
      // Badly-formatted URI encoding.
      BuildPaths.resolvePath("foo%2scala", buildRoot)
    }
    exception.getMessage should startWith("Bad URI in path")
  }

  it should "throw an exception if given a relative buildRoot" in {
    val exception = intercept[IllegalArgumentException] {
      // Badly-formatted URI encoding.
      BuildPaths.resolvePath("foo.scala", new URI("jkinkead/project"))
    }
    exception.getMessage should include("Build root")
  }
}
