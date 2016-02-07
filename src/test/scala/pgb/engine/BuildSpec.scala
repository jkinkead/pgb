package pgb.engine

import pgb.{
  Artifact,
  BuildState,
  ConfigException,
  FileOutputTask,
  FilesArtifact,
  Input,
  StringArtifact,
  StringOutputTask,
  UnitSpec
}
import pgb.engine.parser.{ Argument, BuildParser, FlatTask, StringArgument, TaskArgument }
import pgb.path.Delete
import pgb.task.{ FileTask, FilesTask, StringTask }

import org.scalatest.BeforeAndAfter

import java.net.URI
import java.nio.file.{ Files, Path, Paths }

import scala.collection.mutable

/** Tests for the Build class. */
class BuildSpec extends UnitSpec with BeforeAndAfter {
  /** Test FlatTask that doesn't rely on positional data to generate exception messages. */
  class TestFlatTask(
      taskType: String,
      name: Option[String],
      arguments: Map[String, Seq[Argument]]
  ) extends FlatTask(taskType, name, arguments) {
    /** Throws a config exception with the given error message. */
    override def configException(str: String): Nothing = throw new ConfigException(str)
  }
  object TestFlatTask {
    /** Case-class style factory method. */
    def apply(
      taskType: String,
      name: Option[String],
      arguments: Map[String, Seq[Argument]]
    ): TestFlatTask = new TestFlatTask(taskType, name, arguments)
  }

  val buildFileName = "build.pgb"
  val EmptyBuild = FlatBuild(Map.empty, Set.empty, Set.empty, Map.empty)
  val EmptyParents = mutable.LinkedHashSet.empty[URI]

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
    val badInclude = TestFlatTask("include", None, Map.empty)
    val exception = intercept[ConfigException] {
      testBuild.validateReferenceTask(badInclude)
    }
    exception.getMessage should include(""""include" is missing a name""")
  }

  it should "throw an exception if the task has extra arguments" in {
    val badInclude = TestFlatTask("include", Some("foo.pgb"), Map("foo" -> Seq.empty))
    val exception = intercept[ConfigException] {
      testBuild.validateReferenceTask(badInclude)
    }
    exception.getMessage should include(""""include" task takes no arguments""")
  }

  it should "return the task's name if it's valid" in {
    val badInclude = TestFlatTask("include", Some("foo.pgb"), Map.empty)
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

  trait TestTaskFixture {
    /** A simple task which accepts one argument. */
    class TestTask(
        override val argumentTypes: Map[String, Input.Type],
        override val allowUnknownArguments: Boolean
    ) extends FileOutputTask {
      override val taskType = "testy"

      override def executeValidated(
        name: Option[String],
        stringArguments: Map[String, Seq[StringArtifact]],
        fileArguments: Map[String, FilesArtifact],
        buildState: BuildState
      ): Artifact = ???
    }

    val emptyBuildGraph = new BuildGraph(
      Map.empty, FlatBuild(Map.empty, Set.empty, Set.empty, Map.empty)
    )

    val fileTask = FlatTask("file", Some("/dev/null"), Map.empty)
    val fileTaskArgument = TaskArgument(fileTask)
    val fileTaskNode = new BuildNode(None, fileTask.name, Map.empty, FileTask)
  }

  "validateFlatTask" should "build a node for a simple file task" in new TestTaskFixture {
    val testTaskImpl = new TestTask(Map.empty, allowUnknownArguments = true)
    testBuild.taskRegistry.put("testy", testTaskImpl)
    val testTask = TestFlatTask("testy", Some("name"), Map.empty)
    val (resultNode, resultGraph) =
      testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)
    resultNode.name shouldBe Some("name")
    resultNode.arguments shouldBe Map.empty
    resultNode.task.asInstanceOf[FileOutputTask] shouldBe testTaskImpl
  }

  it should "fail when an unexpected argument occurs and unknown arguments are disallowed" in {
    new TestTaskFixture {
      val testTaskImpl = new TestTask(Map.empty, allowUnknownArguments = false)
      testBuild.taskRegistry.put("testy", testTaskImpl)
      val testTask = TestFlatTask("testy", Some("name"), Map("bad" -> Seq(fileTaskArgument)))
      val error = intercept[ConfigException] {
        testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)
      }

      error.getMessage should include("""given unknown argument "bad"""")
    }
  }

  it should "allow unexpected arguments when unknown arguments are allowed" in new TestTaskFixture {
    val testTaskImpl = new TestTask(Map.empty, allowUnknownArguments = true)
    testBuild.taskRegistry.put("testy", testTaskImpl)
    val testTask = TestFlatTask("testy", Some("name"), Map("ignore" -> Seq(fileTaskArgument)))
    val (resultNode, resultGraph) =
      testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)

    resultNode.name shouldBe Some("name")
    resultNode.arguments should have size (1)
    resultNode.arguments should contain key "ignore"
    resultNode.arguments("ignore") should have size (1)
    val ignoreTask = resultNode.arguments("ignore").head
    ignoreTask.name shouldBe (fileTask.name)
    ignoreTask.arguments shouldBe empty
    ignoreTask.task.asInstanceOf[FileOutputTask] shouldBe FileTask
    resultNode.task.asInstanceOf[FileOutputTask] shouldBe testTaskImpl
  }

  it should "fail if an argument's type doesn't match" in new TestTaskFixture {
    val testTaskImpl = new TestTask(Map("file" -> Input.OptionalFile), false)
    testBuild.taskRegistry.put("testy", testTaskImpl)
    val testTask = TestFlatTask(
      "testy",
      Some("name"),
      Map("file" -> Seq(TaskArgument(TestFlatTask("string", Some("filename"), Map.empty))))
    )
    val error = intercept[ConfigException] {
      testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)
    }
    error.getMessage should include("""argument "file" requires files""")
  }

  it should "implicitly convert a string argument to a file argument" in new TestTaskFixture {
    val testTaskImpl = new TestTask(Map("file" -> Input.OptionalFile), false)
    testBuild.taskRegistry.put("testy", testTaskImpl)
    val testTask = TestFlatTask(
      "testy",
      Some("name"),
      Map("file" -> Seq(StringArgument("filename")))
    )
    val (resultNode, resultGraph) =
      testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)

    resultNode.name shouldBe Some("name")
    resultNode.arguments should have size (1)
    resultNode.arguments should contain key "file"
    resultNode.arguments("file") should have size (1)
    val fileArgument = resultNode.arguments("file").head
    fileArgument.name shouldBe (Some("filename"))
    fileArgument.arguments shouldBe empty
    fileArgument.task.asInstanceOf[FileOutputTask] shouldBe FilesTask
    resultNode.task.asInstanceOf[FileOutputTask] shouldBe testTaskImpl
  }

  it should "leave a valid string argument unchanged" in new TestTaskFixture {
    val testTaskImpl = new TestTask(Map("str" -> Input.RequiredString), false)
    testBuild.taskRegistry.put("testy", testTaskImpl)
    val testTask = TestFlatTask(
      "testy",
      Some("name"),
      Map("str" -> Seq(StringArgument("stringy")))
    )
    val (resultNode, resultGraph) =
      testBuild.validateFlatTask(testTask, buildFile.toUri, EmptyParents, emptyBuildGraph)

    resultNode.name shouldBe Some("name")
    resultNode.arguments should have size (1)
    resultNode.arguments should contain key "str"
    resultNode.arguments("str") should have size (1)
    val stringArgument = resultNode.arguments("str").head
    stringArgument.name shouldBe (Some("stringy"))
    stringArgument.arguments shouldBe empty
    stringArgument.task.asInstanceOf[StringOutputTask] shouldBe StringTask
    resultNode.task.asInstanceOf[FileOutputTask] shouldBe testTaskImpl
  }
}
