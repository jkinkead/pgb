package pgb.path

import pgb.{ ConfigException, UnitSpec }

import org.scalatest.BeforeAndAfter

import java.net.URI
import java.nio.file.{ Files, Path }

class ResolverSpec extends UnitSpec with BeforeAndAfter {
  var tempRoot: Path = _
  var buildUri: URI = _

  before {
    // Create a build structure with some expected files.
    tempRoot = Files.createTempDirectory(null)
    Files.createDirectory(tempRoot.resolve("src"))
    Files.createDirectory(tempRoot.resolve("src/main"))
    Files.createDirectory(tempRoot.resolve("src/main/scala"))
    Files.createDirectory(tempRoot.resolve("src/main/java"))
    Files.createFile(tempRoot.resolve("foo.txt"))
    Files.createFile(tempRoot.resolve("bar.txt"))
    Files.createFile(tempRoot.resolve("src/foo.txt"))
    Files.createFile(tempRoot.resolve("src/main/scala/Foo.scala"))
    Files.createFile(tempRoot.resolve("src/main/java/Foo.java"))

    val buildRoot = Files.createFile(tempRoot.resolve("build.pgb"))
    buildUri = buildRoot.toUri
  }

  after {
    Delete.delete(tempRoot)
  }

  "toPathUri" should "fail on opaque paths" in {
    val exception = intercept[ConfigException] {
      Resolver.toPathUri("file:src/foo.scala")
    }
    exception.getMessage should startWith("Non-absolute URI")
  }

  it should "work on an absolute file path" in {
    val path = "file:///home/jkinkead/other/src/foo.scala"
    val result = Resolver.toPathUri(path)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "work on an absolute github path" in {
    val path = "github:///jkinkead/project/foo.pgb#task"
    val result = Resolver.toPathUri(path)
    // Result should be an unmodified path.
    result shouldBe new URI(path)
  }

  it should "canonicalize an absolute path" in {
    val result = Resolver.toPathUri("file:///jkinkead/other/src/main/../test/foo.scala")
    // Result should be a canonicalized path.
    result shouldBe new URI("file:///jkinkead/other/src/test/foo.scala")
  }

  it should "canonicalize a relative path" in {
    val result = Resolver.toPathUri("test/../main/foo.scala")
    // Result should be a canonicalized path.
    result shouldBe new URI("main/foo.scala")
  }

  it should "throw an exception for a badly-formatted URI" in {
    val exception = intercept[ConfigException] {
      // Badly-formatted URI encoding.
      Resolver.toPathUri("foo%2scala")
    }
    exception.getMessage should startWith("Bad URI in path")
  }

  "resolvePath" should "resolve a relative file path" in {
    val results = Resolver.resolvePath("foo.txt", buildUri)
    results shouldBe Seq(tempRoot.resolve("foo.txt").toFile)
  }

  it should "resolve an absolute file path" in {
    val path = tempRoot.resolve("bar.txt")
    val results = Resolver.resolvePath(path.toUri.toString, buildUri)
    results shouldBe Seq(path.toFile)
  }

  it should "resolve a simple lookbehind path" in {
    val newRoot = tempRoot.resolve("src/main/scala")
    val results = Resolver.resolvePath(".../foo.txt", newRoot.toUri)
    results shouldBe Seq(tempRoot.resolve("src/foo.txt").toFile)
  }

  it should "resolve a relative lookbehind path" in {
    val newRoot = tempRoot.resolve("src/main/scala")
    val results = Resolver.resolvePath(".../src/foo.txt", newRoot.toUri)
    results shouldBe Seq(tempRoot.resolve("src/foo.txt").toFile)
  }

  it should "resolve a globbed lookbehind path" in {
    val newRoot = tempRoot.resolve("src/main/scala")
    val results = Resolver.resolvePath(".../b*.txt", newRoot.toUri)
    results shouldBe Seq(tempRoot.resolve("bar.txt").toFile)
  }

  it should "resolve an empty lookbehind path" in {
    val newRoot = tempRoot.resolve("src/main/scala")
    val results = Resolver.resolvePath(".../gaz.txt", newRoot.toUri)
    results shouldBe Seq.empty
  }

  it should "resolve a lookbehind path in the same directory" in {
    val newRoot = tempRoot.resolve("src/main/scala")
    val results = Resolver.resolvePath(".../Foo.scala", newRoot.toUri)
    results shouldBe Seq(newRoot.resolve("Foo.scala").toFile)
  }

  it should "skip missing files" in {
    val path = tempRoot.resolve("gaz.txt")
    val results = Resolver.resolvePath(path.toUri.toString, buildUri)
    results shouldBe Seq.empty
  }

  it should "resolve a simple glob" in {
    val results = Resolver.resolvePath("*.txt", buildUri)
    val expected = Seq(tempRoot.resolve("foo.txt").toFile, tempRoot.resolve("bar.txt").toFile)
    results.sorted shouldBe expected.sorted
  }

  it should "resolve complicated globs" in {
    val results = Resolver.resolvePath("**.txt", buildUri)
    val expected = Seq(
      tempRoot.resolve("foo.txt").toFile,
      tempRoot.resolve("bar.txt").toFile,
      tempRoot.resolve("src/foo.txt").toFile
    )
    results.sorted shouldBe expected.sorted
  }

  it should "resolve nested globs" in {
    val results = Resolver.resolvePath("**/Foo.*", buildUri)
    val expected = Seq(
      tempRoot.resolve("src/main/scala/Foo.scala").toFile,
      tempRoot.resolve("src/main/java/Foo.java").toFile
    )
    results.sorted shouldBe expected.sorted
  }

  it should "resolve directories" in {
    val results = Resolver.resolvePath("**/*a*", buildUri)
    val expected = Seq(
      tempRoot.resolve("src/main").toFile,
      tempRoot.resolve("src/main/java").toFile,
      tempRoot.resolve("src/main/java/Foo.java").toFile,
      tempRoot.resolve("src/main/scala").toFile,
      tempRoot.resolve("src/main/scala/Foo.scala").toFile
    )
    results.sorted shouldBe expected.sorted
  }

  it should "throw an exception if given a relative build root" in {
    val exception = intercept[IllegalArgumentException] {
      // Badly-formatted URI encoding.
      Resolver.resolvePath("foo.scala", new URI("jkinkead/project"))
    }
    exception.getMessage should include("Build root")
  }

  it should "throw an exception if given an unknown scheme" in {
    val exception = intercept[ConfigException] {
      Resolver.resolveSingleFilePath("ftp://foo.com/Foo.scala", buildUri)
    }
    exception.getMessage should include("Unhandled scheme")
  }

  "resolveSingleFilePath" should "resolve a glob into a single file" in {
    val result = Resolver.resolveSingleFilePath("src/main/scala/**.scala", buildUri)
    result shouldBe tempRoot.resolve("src/main/scala/Foo.scala").toFile
  }

  it should "throw an exception if given a path that resolves to no files" in {
    val exception = intercept[ConfigException] {
      Resolver.resolveSingleFilePath("**.sc", buildUri)
    }
    exception.getMessage should include("resolved to no files")
  }

  it should "throw an exception if given a path that resolves to multiple files" in {
    val exception = intercept[ConfigException] {
      Resolver.resolveSingleFilePath("**.txt", buildUri)
    }
    exception.getMessage should include("resolved to multiple files")
  }
}
