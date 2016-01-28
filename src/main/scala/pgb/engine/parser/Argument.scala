package pgb.engine.parser

/** An partially-parsed argument. Used internally by the parser. */
private[parser] sealed trait RawArgument

/** The value of a named argument. Can be either a string or a task. */
sealed trait Argument

/** A bareword string argument. */
case class StringArgument(value: String) extends Argument with RawArgument

/** A partially-parsed task argument, used internally by the parser. */
private[parser] case class RawTaskArgument(value: RawTask) extends RawArgument

/** A fully-parsed task argument. */
case class TaskArgument(value: FlatTask) extends Argument
