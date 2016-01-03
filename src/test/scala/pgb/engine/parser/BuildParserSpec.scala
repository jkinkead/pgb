package pgb.engine.parser

import pgb.UnitSpec

import org.scalatest.matchers.Matcher

/** Tests for the build file parser. */
class BuildParserSpec extends UnitSpec {

  val testParser = new BuildParser
  import testParser.{ Error, Failure, ParseResult, Success }

  /** @return the successful parse result, or a failure if it's in error */
  def success[T](result: ParseResult[T]): T = {
    result match {
      case Success(parsedValue, _) => parsedValue
      case Failure(message, _) => fail(message)
      case Error(message, _) => fail(message)
    }
  }

  /** Matches an expected value of a parse result against the parse result. */
  def beSuccess[T](expectedValue: T): Matcher[ParseResult[T]] = {
    be(expectedValue) compose success[T] _
  }

  /** @return the failure message for the given parse result, or a failure if it's successful */
  def failure(result: ParseResult[_]): String = {
    result match {
      case Success(parsedValue, _) => fail("successfully parsed: " + parsedValue)
      case Failure(message, _) => message
      case Error(message, _) => message
    }
  }

  /** Matches an expected error message against the parse result. */
  def includeFailureMessage(message: String): Matcher[ParseResult[_]] = {
    include(message) compose failure _
  }

  "stringLiteral" should "parse a quoted string" in {
    val result = testParser.parseAll(testParser.stringLiteral, """ "quoted" """)
    result should beSuccess("quoted")
  }

  it should "parse a string with escaped quotes and backslashes" in {
    val result = {
      testParser.parseAll(testParser.stringLiteral, """ "doublequote: \", backslash: \\"""")
    }
    result should beSuccess("""doublequote: ", backslash: \""")
  }

  "name" should "parse a bareword" in {
    val result = testParser.parseAll(testParser.name, " bareword ")
    result should beSuccess("bareword")
  }

  "argument" should "parse a string-valued argument" in {
    val result = testParser.parseAll(testParser.argument, """foo = "./bar.txt" """)
    result should beSuccess(Argument("foo", Seq(StringArgument("./bar.txt"))))
  }

  it should "parse an argument with multiple string values" in {
    val result = testParser.parseAll(testParser.argument, """foo = ["bar", "gaz"]""")
    result should beSuccess(Argument("foo", Seq(StringArgument("bar"), StringArgument("gaz"))))
  }

  trait TaskFixture {
    val fooName = Map("name" -> Argument("name", Seq(StringArgument("./foo.txt"))))
    val barName = Map("name" -> Argument("name", Seq(StringArgument("bar"))))

    val fooTask = FlatTask("file", fooName)
    val barTask = FlatTask("file", barName)
  }

  "task" should "parse a task with just a name" in new TaskFixture {
    val result = testParser.parseAll(testParser.task, """file("./foo.txt")""")
    result should beSuccess(fooTask)
  }

  it should "parse a task with string arguments" in new TaskFixture {
    val result = testParser.parseAll(
      testParser.task,
      """file("./foo.txt", one = "one", two = ["t", "w", "o"])"""
    )
    result should beSuccess(
      FlatTask(
        "file",
        fooName ++ Map(
          "one" -> Argument("one", Seq(StringArgument("one"))),
          "two" ->
            Argument("two", Seq(StringArgument("t"), StringArgument("w"), StringArgument("o")))
        )
      )
    )
  }

  it should "parse a task with task arguments" in new TaskFixture {
    val result = testParser.parseAll(testParser.task, """file("./foo.txt", arg = file("bar"))""")
    result should beSuccess(
      FlatTask(
        "file",
        fooName ++ Map(
          "arg" -> Argument("arg", Seq(TaskArgument(barTask)))
        )
      )
    )
  }

  it should "handle comments correctly" in new TaskFixture {
    val result = testParser.parseAll(testParser.task, """
      # The Foo file.
      file(
        "./foo.txt" # It's called "foo.txt".
      # Multi-line comment.

      # Continues!
      )
    """)
    result should beSuccess(fooTask)
  }

  it should "detect duplicate arguments" in {
    val result = testParser.parseAll(testParser.task, """file("bar", arg = "a", arg = "b")""")
    result should includeFailureMessage("""argument "arg"""")
  }

  it should "detect duplicate name arguments" in {
    val result = testParser.parseAll(testParser.task, """file("bar", name = "a", arg = "b")""")
    result should includeFailureMessage(""""name" was provided""")
  }

  "parseBuildFile" should "handle a simple file" in new TaskFixture {
    val result = testParser.parseBuildFile("""
      # A whole build file.
      file("./foo.txt")

      file("bar", arg=task("foo"))
      # Done!
    """)
    result should beSuccess(
      Seq(
        fooTask,
        FlatTask(
          "file",
          barName ++ Map(
            "arg" -> Argument("arg", Seq(TaskArgument(
              FlatTask("task", Map("name" -> Argument("name", Seq(StringArgument("foo")))))
            )))
          )
        )
      )
    )
  }
}
