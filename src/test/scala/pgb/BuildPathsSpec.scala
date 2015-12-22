package pgb

import java.net.URI

class BuildPathsSpec extends UnitSpec {
  val buildRoot = new URI("file:///home/jkinkead/build.pgb")

  /*
  "resolvePath" should "resolve a relative file path" in {
    val result = BuildPaths.resolvePath("src/foo.scala", buildRoot)
    result shouldBe new URI("file:///home/jkinkead/src/foo.scala")
  }

  it should "resolve paths with special characters" in {
    val result = BuildPaths.resolvePath(".../src/**/foo*.scala", buildRoot)
    result shouldBe new URI("file:///home/jkinkead/.../src/**/foo*.scala")
  }

  it should "resolve paths with only a fragment" in {
    val result = BuildPaths.resolvePath("#task", buildRoot)
    result shouldBe new URI("file:///home/jkinkead/build.pgb#task")
  }

  it should "throw an exception if given a relative buildRoot" in {
    val exception = intercept[IllegalArgumentException] {
      // Badly-formatted URI encoding.
      BuildPaths.resolvePath("foo.scala", new URI("jkinkead/project"))
    }
    exception.getMessage should include("Build root")
  }
  */

  "toPathUri" should "fail on opaque paths" in {
    val exception = intercept[ConfigException] {
      BuildPaths.toPathUri("file:src/foo.scala")
    }
    exception.getMessage should startWith("Non-absolute URI")
  }

  it should "work on an absolute file path" in {
    val path = "file:///home/jkinkead/other/src/foo.scala"
    val result = BuildPaths.toPathUri(path)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "work on an absolute github path" in {
    val path = "github:///jkinkead/project/foo.pgb#task"
    val result = BuildPaths.toPathUri(path)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "canonicalize an absolute path" in {
    val result = BuildPaths.toPathUri("file:///jkinkead/other/src/main/../test/foo.scala")
    // Result should be a canonicalized path.
    result shouldBe new URI("file:///jkinkead/other/src/test/foo.scala")
  }

  it should "canonicalize a relative path" in {
    val result = BuildPaths.toPathUri("test/../main/foo.scala")
    // Result should be a canonicalized path.
    result shouldBe new URI("main/foo.scala")
  }

  it should "throw an exception for a badly-formatted URI" in {
    val exception = intercept[ConfigException] {
      // Badly-formatted URI encoding.
      BuildPaths.toPathUri("foo%2scala")
    }
    exception.getMessage should startWith("Bad URI in path")
  }

  "FileGlob" should "match a basic path correctly" in {
    val path = "src/main/scala/**/*.scala"
    val matchOption = BuildPaths.FileGlob.findFirstMatchIn(path)
    matchOption shouldBe 'nonEmpty
    val m = matchOption.get
    m.before shouldBe "src/main/scala"
    path.substring(m.start) shouldBe "/**/*.scala"
  }

  it should "match a path starting with a glob" in {
    val path = "**/*.java"
    val matchOption = BuildPaths.FileGlob.findFirstMatchIn(path)
    matchOption shouldBe 'nonEmpty
    val m = matchOption.get
    m.before shouldBe ""
    path.substring(m.start) shouldBe "**/*.java"
  }

  it should "match an escaped path correctly" in {
    val path = """src/ma\*in/scala/*.scala"""
    val matchOption = BuildPaths.FileGlob.findFirstMatchIn(path)
    matchOption shouldBe 'nonEmpty
    val m = matchOption.get
    m.before shouldBe """src/ma\*in/scala"""
    path.substring(m.start) shouldBe "/*.scala"
  }
}
