package pgb.engine.parser

import pgb.UnitSpec

import org.scalatest.matchers.Matcher

/** Tests for the build file parser. */
class BuildParserSpec extends UnitSpec {

  val testParser = new BuildParser
  import testParser.{ Error, Failure, ParseResult, Success }

  /** @return the successful parse result, or a failure if it's in error. */
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

  "task" should "parse a task with just a name" in {
    val result = testParser.parseAll(testParser.task, """file("./foo.txt")""")
    result should beSuccess(FlatTask("file", Some("./foo.txt"), Seq.empty))
  }

  it should "parse a task with string arguments" in {
    val result = testParser.parseAll(
      testParser.task,
      """file("./foo.txt", one = "one", two = ["t", "w", "o"])"""
    )
    result should beSuccess(
      FlatTask(
        "file",
        Some("./foo.txt"),
        Seq(
          Argument("one", Seq(StringArgument("one"))),
          Argument("two", Seq(StringArgument("t"), StringArgument("w"), StringArgument("o")))
        )
      )
    )
  }

  it should "parse a task with task arguments" in {
    val result = testParser.parseAll(testParser.task, """file("foo", arg = file("bar"))""")
    result should beSuccess(
      FlatTask(
        "file",
        Some("foo"),
        Seq(Argument("arg", Seq(TaskArgument(FlatTask("file", Some("bar"), Seq.empty)))))
      )
    )
  }

  it should "handle comments correctly" in {
    val result = testParser.parseAll(testParser.task, """
      # The Foo file.
      file(
        "foo" # It's called "foo".
      # Multi-line comment.

      # Continues!
      )
    """)
    result should beSuccess(FlatTask("file", Some("foo"), Seq.empty))
  }

  "parseBuildFile" should "handle a simple file" in {
    val result = testParser.parseBuildFile("""
      # A whole build file.
      file("foo")

      file("bar", arg=task("foo"))
      # Done!
    """)
    result should beSuccess(
      Seq(
        FlatTask("file", Some("foo"), Seq.empty),
        FlatTask(
          "file",
          Some("bar"),
          Seq(Argument("arg", Seq(TaskArgument(FlatTask("task", Some("foo"), Seq.empty)))))
        )
      )
    )
  }
}
