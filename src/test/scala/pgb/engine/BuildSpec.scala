package pgb.engine

import pgb.{ ConfigException, UnitSpec }
import pgb.engine.parser.{ Argument, BuildParser, FlatTask }
import pgb.path.Delete

import org.scalatest.BeforeAndAfter

import java.net.URI
import java.nio.file.{ Files, Path, Paths }

/** Tests for the Build class. */
class BuildSpec extends UnitSpec with BeforeAndAfter {
  /** Test FlatTask that doesn't rely on positional data to generate exception messages. */
  class TestFlatTask(taskType: String, name: Option[String], arguments: Map[String, Seq[Argument]])
      extends FlatTask(taskType, name, arguments) {
    /** Throws a config exception with the given error message. */
    override def configException(str: String): Nothing = throw new ConfigException(str)
  }

  val buildFileName = "build.pgb"
  val EmptyBuild = FlatBuild(Map.empty, Set.empty, Set.empty, Map.empty)

  /** The directory the build will run in. */
  var workingDir: URI = _
  /** The path to the build file to populate. */
  var buildFile: Path = _

  before {
    val workingPath = Files.createTempDirectory(null)
    workingDir = workingPath.toUri
    buildFile = workingPath.resolve(buildFileName)
    testBuild = new Build(parser, workingDir)
  }

  after {
    Delete.delete(Paths.get(workingDir))
  }

  /** Populates the build file with the given contents. */
  def populateBuildFile(contents: String): Unit = {
    val writer = Files.newBufferedWriter(buildFile)
    writer.write(contents)
    writer.close()
  }

  val parser = new BuildParser
  var testBuild: Build = _

  "validateReferenceTask" should "throw an exception if the task has no name" in {
    val badInclude = new TestFlatTask("include", None, Map.empty)
    val exception = intercept[ConfigException] {
      testBuild.validateReferenceTask(badInclude)
    }
    exception.getMessage should include(""""include" is missing a name""")
  }

  it should "throw an exception if the task has extra arguments" in {
    val badInclude = new TestFlatTask(
      "include", Some("foo.pgb"), Map("foo" -> Seq.empty)
    )
    val exception = intercept[ConfigException] {
      testBuild.validateReferenceTask(badInclude)
    }
    exception.getMessage should include(""""include" task takes no arguments""")
  }

  it should "return the task's name if it's valid" in {
    val badInclude = new TestFlatTask("include", Some("foo.pgb"), Map.empty)
    testBuild.validateReferenceTask(badInclude) shouldBe "foo.pgb"
  }

  "loadBuildFile" should "update an empty state" in {
    populateBuildFile("""
      |task("foo")
      |include("foo.pgb")
      |task("bar")
    """.stripMargin)
    val result = testBuild.loadBuildFile(EmptyBuild, buildFile.toUri)
    result.tasks should have size 2
    val fooKey = buildFile.toUri.resolve("#foo")
    result.tasks should contain key fooKey
    result.tasks(fooKey) shouldBe (FlatTask("task", Some("foo"), Map.empty))
    val barKey = buildFile.toUri.resolve("#bar")
    result.tasks should contain key barKey
    result.tasks(barKey) shouldBe (FlatTask("task", Some("bar"), Map.empty))
    result.resolvedIncludes should be(Set(buildFile.toFile))
    result.unresolvedIncludes should be(Set(workingDir.resolve("foo.pgb")))
    result.taskDefs should be('empty)
  }

  it should "update a non-empty state" in {
    val nonEmptyBuild = FlatBuild(
      Map(buildFile.toUri.resolve("./foo.pgb#foo") -> FlatTask("task", Some("foo"), Map.empty)),
      Set(Paths.get(workingDir.resolve("foo.pgb")).toFile),
      Set(buildFile.toUri.resolve("./bar.pgb")),
      Map.empty
    )
    populateBuildFile("""
      |# Duplicate an existing include.
      |include("bar.pgb")
      |task("foo")
    """.stripMargin)
    val result = testBuild.loadBuildFile(nonEmptyBuild, buildFile.toUri)
    result.tasks should have size 2
    val foo1Key = buildFile.toUri.resolve("#foo")
    result.tasks should contain key foo1Key
    result.tasks(foo1Key) shouldBe (FlatTask("task", Some("foo"), Map.empty))
    val foo2key = workingDir.resolve("./foo.pgb#foo")
    result.tasks should contain key foo2key
    result.tasks(foo2key) shouldBe (FlatTask("task", Some("foo"), Map.empty))
    result.resolvedIncludes should be(Set(
      buildFile.toFile,
      Paths.get(workingDir.resolve("foo.pgb")).toFile
    ))
    result.unresolvedIncludes should be(Set(workingDir.resolve("bar.pgb")))
    result.taskDefs should be('empty)
  }
}
