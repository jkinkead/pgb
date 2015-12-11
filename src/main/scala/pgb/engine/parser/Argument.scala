package pgb.engine.parser

/** The value of a named argument. Can be either a string or a task. */
sealed abstract class ArgumentValue
case class StringArgument(value: String) extends ArgumentValue
case class TaskArgument(value: FlatTask) extends ArgumentValue

/** An argument to a task parsed from a build file. */
case class Argument(name: String, values: Seq[ArgumentValue])
