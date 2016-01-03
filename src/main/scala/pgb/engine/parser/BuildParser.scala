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
      case taskType ~ name ~ Some(arguments) => RawTask(taskType, Some(name), arguments)
      case taskType ~ name ~ None => RawTask(taskType, Some(name), Seq.empty)
    } into {
      validateTask
    }
  }

  /** A task with the name specified as an argument, e.g. `name = "foo"`. */
  def taskOnlyArgs: Parser[FlatTask] = (name <~ "(") ~ repsep(argument, ",") <~ ")" ^^ {
    case taskType ~ arguments => RawTask(taskType, None, arguments)
  } into { validateTask }

  /** Validates a raw task, converting it into a FlatTask. Errors if the task contains duplicated
    * arguments.
    */
  def validateTask(task: RawTask): Parser[FlatTask] = {
    val allArguments = task.arguments ++ (task.name map { name =>
      Argument("name", Seq(StringArgument(name)))
    })
    // Convert the arguments list to a map.
    val argumentsMap = (allArguments map { argument => argument.name -> argument }).toMap
    if (argumentsMap.size != task.arguments.size + 1) {
      // Figure out if any arguments were specified twice.
      val specifiedNames = task.arguments groupBy { _.name }
      val duplicateOption = specifiedNames find {
        case (_, values) => values.length > 1
      } map {
        case (name, values) => name
      }

      val errorMessage = duplicateOption match {
        case Some(name) => s"""argument "$name" was provided multiple times"""
        case None => """"name" was provided as named argument and default argument"""
      }
      err(errorMessage)
    } else {
      success(FlatTask(task.taskType, argumentsMap))
    }
  }

  /** Task, e.g. `task_type("name", arg1 = "args", arg2 = ["etc"])` */
  def task: Parser[FlatTask] = taskWithName | taskOnlyArgs

  /** Whole file - any number of tasks. */
  def buildFile: Parser[Seq[FlatTask]] = task.*

  def parseBuildFile(infile: String): ParseResult[Seq[FlatTask]] = {
    parseAll(buildFile, infile)
  }
}
