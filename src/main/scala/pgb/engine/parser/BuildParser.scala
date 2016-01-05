package pgb.engine.parser

import pgb.ConfigException

import java.io.File

import scala.io.Source
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.Position

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
  val taskArgument: Parser[RawTaskArgument] = task ^^ { RawTaskArgument(_) }

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
  def taskWithName: Parser[RawTask] = {
    (name <~ "(") ~ stringLiteral ~ ("," ~> repsep(argument, ",")).? <~ ")" ^^ {
      case taskType ~ name ~ Some(arguments) => RawTask(taskType, Some(name), arguments)
      case taskType ~ name ~ None => RawTask(taskType, Some(name), Seq.empty)
    }
  }

  /** A task with the name specified as an argument, e.g. `name = "foo"`. */
  def taskOnlyArgs: Parser[RawTask] = (name <~ "(") ~ repsep(argument, ",") <~ ")" ^^ {
    case taskType ~ arguments => RawTask(taskType, None, arguments)
  }

  /** Validates a raw task, converting it into a FlatTask, or throwing a ConfigException. */
  def validateTask(task: RawTask, filename: String, contents: String): FlatTask = {
    val allArguments = task.arguments ++ (task.name map { name =>
      Argument("name", Seq(StringArgument(name)))
    })
    // Convert the arguments list to a map.
    val argumentsMap = (allArguments map { argument =>
      val newValues = argument.values map {
        case RawTaskArgument(rawTask) => TaskArgument(validateTask(rawTask, filename, contents))
        case other => other
      }
      argument.copy(values = newValues)
    } map { argument => argument.name -> argument }).toMap
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
      throw new ConfigException(Util.exceptionMessage(errorMessage, filename, contents, task.pos))
    } else {
      val newTask = FlatTask(task.taskType, argumentsMap)
      newTask.setPos(task.pos)
      newTask.setFileInfo(filename, contents)
      newTask
    }
  }

  /** Task, e.g. `task_type("name", arg1 = "args", arg2 = ["etc"])` */
  def task: Parser[RawTask] = positioned { taskWithName | taskOnlyArgs }

  /** Whole file - any number of tasks. */
  def buildFile: Parser[Seq[RawTask]] = task.*

  /** Translates a ParseResult, throwing a ConfigException if there was a parsing problem. */
  def extractResult[T](filename: String, result: ParseResult[T]): T = {
    result match {
      case Success(tasks, _) => tasks
      case NoSuccess(message, next) => {
        throw new ConfigException(
          Util.exceptionMessage(message, filename, next.source.toString, next.pos)
        )
      }
    }
  }

  private[parser] def parseBuildFileInternal(filename: String, contents: String): Seq[FlatTask] = {
    extractResult(filename, parseAll(buildFile, contents)) map { task =>
      validateTask(task, filename, contents)
    }
  }

  /** Parses a given build file. */
  def parseBuildFile(infile: File): Seq[FlatTask] = {
    parseBuildFileInternal(infile.getAbsolutePath, Source.fromFile(infile).mkString)
  }
}
