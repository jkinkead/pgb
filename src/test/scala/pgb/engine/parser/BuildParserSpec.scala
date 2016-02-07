package pgb.engine.parser

import pgb.{ ConfigException, UnitSpec }

import org.scalatest.matchers.Matcher

/** Tests for the build file parser. */
class BuildParserSpec extends UnitSpec {
  val filename = "/home/jkinkead/build.pgb"

  val testParser = new BuildParser
  import testParser.{ NoSuccess, ParseResult, Success }

  /** @return the successful parse result, or a failure if it's in error */
  def success[T](result: ParseResult[T]): T = {
    result match {
      case Success(parsedValue, _) => parsedValue
      case NoSuccess(message, _) => fail(message)
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
      case NoSuccess(message, _) => message
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
    result should beSuccess("foo" -> Seq[RawArgument](StringArgument("./bar.txt")))
  }

  it should "parse an argument with multiple string values" in {
    val result = testParser.parseAll(testParser.argument, """foo = ["bar", "gaz"]""")
    result should beSuccess("foo" -> Seq[RawArgument](StringArgument("bar"), StringArgument("gaz")))
  }

  it should "parse a task reference argument" in {
    val result = testParser.parseAll(testParser.argument, """foo = barTask""")
    result should beSuccess("foo" -> Seq[RawArgument](TaskRefArgument("barTask")))
  }

  trait TaskFixture {
    val fooName = Some("./foo.txt")
    val barName = Some("bar")

    val fooTask = RawTask("file", fooName, Seq.empty)
    val barTask = RawTask("file", barName, Seq.empty)
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
      RawTask(
        "file",
        fooName,
        Seq(
          "one" -> Seq(StringArgument("one")),
          "two" -> Seq(StringArgument("t"), StringArgument("w"), StringArgument("o"))
        )
      )
    )
  }

  it should "parse a task with task arguments" in new TaskFixture {
    val result = testParser.parseAll(testParser.task, """file("./foo.txt", arg = file("bar"))""")
    result should beSuccess(
      RawTask(
        "file",
        fooName,
        Seq("arg" -> Seq(RawTaskArgument(barTask)))
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

  "parseBuildFileInternal" should "handle a simple file" in new TaskFixture {
    val result = testParser.parseBuildFileInternal(filename, """
      # A whole build file.
      file("./foo.txt")

      file("bar", arg=task("foo"))
      # Done!
    """)
    result should be(
      Seq(
        FlatTask(
          "file",
          fooName,
          Map.empty
        ),
        FlatTask(
          "file",
          barName,
          Map(
            "arg" -> Seq(TaskArgument(
              FlatTask("task", Some("foo"), Map.empty)
            ))
          )
        )
      )
    )
  }

  it should "detect duplicate arguments" in {
    val exception = intercept[ConfigException] {
      testParser.parseBuildFileInternal(filename, """file("bar", arg = "a", arg = "b")""")
    }
    exception.getMessage should include("""argument "arg"""")
  }

  it should "detect duplicate name arguments" in {
    val exception = intercept[ConfigException] {
      testParser.parseBuildFileInternal(filename, """file("bar", name = "a", arg = "b")""")
    }
    exception.getMessage should include(""""name" was provided""")
  }

  it should "detect multi-valued name arguments" in {
    val exception = intercept[ConfigException] {
      testParser.parseBuildFileInternal(filename, """file(name = ["a", "b"], arg = "b")""")
    }
    exception.getMessage should include(""""name" argument contained multiple""")
  }

  it should "detect empty name arguments" in {
    val exception = intercept[ConfigException] {
      testParser.parseBuildFileInternal(filename, """file(name = [], arg = "b")""")
    }
    exception.getMessage should include(""""name" argument contained no""")
  }

  it should "detect name arguments with non-literal values" in {
    val exception = intercept[ConfigException] {
      testParser.parseBuildFileInternal(filename, """file(name = file("foo"), arg = "b")""")
    }
    exception.getMessage should include(""""name" argument contained a non-literal""")
  }
}
