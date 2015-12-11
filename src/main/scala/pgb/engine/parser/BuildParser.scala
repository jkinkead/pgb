package pgb.engine.parser

import scala.util.parsing.combinator.RegexParsers

/** Parser for build files. */
class BuildParser extends RegexParsers {
  /** Skip whitespace and shell-style comments. */
  override val whiteSpace = """(\s+|#.*)*""".r

  val EscapedChar = """\\(.)""".r

  /** Double-quoted string, allowing escapes. */
  val stringLiteral: Parser[String] = """"(?:\\["\\]|[^"\\])*"""".r ^^ { quotedStr =>
    // Strip off double-quotes.
    val strippedStr = quotedStr.substring(1, quotedStr.length - 1)
    EscapedChar.replaceAllIn(strippedStr, { matcher =>
      val original = matcher.group(1)
      if (original == "\\") {
        // Java's regex library wants to have this escaped.
        "\\\\"
      } else {
        original
      }
    })
  }

  /** Task name or argument name. */
  val name: Parser[String] = """[\p{IsAlphabetic}_]+""".r

  /** A string-valued argument. */
  val stringArgument: Parser[StringArgument] = stringLiteral ^^ { StringArgument(_) }

  /** A task-valued argument. */
  val taskArgument: Parser[TaskArgument] = task ^^ { TaskArgument(_) }

  /** Any argument value. */
  val argumentValue: Parser[ArgumentValue] = stringArgument | taskArgument

  /** List of arguments, as a javascript-style array. */
  val argumentValueList: Parser[Seq[ArgumentValue]] = "[" ~> repsep(argumentValue, ",") <~ "]"

  /** Single argument value, converted into a one-element sequence. */
  val singleArgumentValue: Parser[Seq[ArgumentValue]] = argumentValue ^^ { Seq(_) }

  /** Parses an argument of the form `name = "value"` or `name = [ "val1", "val2" ]`. */
  val argument: Parser[Argument] = (name <~ "=") ~ (singleArgumentValue | argumentValueList) ^^ {
    case name ~ args => Argument(name, args)
  }

  /** A task with the name as a first anonymous argument. */
  def taskWithName: Parser[FlatTask] = {
    (name <~ "(") ~ stringLiteral ~ ("," ~> repsep(argument, ",")).? <~ ")" ^^ {
      case taskType ~ name ~ Some(arguments) => FlatTask(taskType, Some(name), arguments)
      case taskType ~ name ~ None => FlatTask(taskType, Some(name), Seq.empty)
    }
  }

  /** A task with the name specified as an argument, e.g. `name = "foo"`. */
  def taskOnlyArgs: Parser[FlatTask] = (name <~ "(") ~ repsep(argument, ",") <~ ")" ^^ {
    case taskType ~ arguments => FlatTask(taskType, None, arguments)
  }

  /** Task, of the form task_type("name", arg1 = "args", arg2 = ["etc"]). */
  def task: Parser[FlatTask] = taskWithName | taskOnlyArgs

  /** Whole file - any number of tasks. */
  def buildFile: Parser[Seq[FlatTask]] = task.*

  def parseBuildFile(infile: String): ParseResult[Seq[FlatTask]] = {
    parseAll(buildFile, infile)
  }
}
