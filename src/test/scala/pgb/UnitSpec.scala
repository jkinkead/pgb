package pgb

import org.scalatest.{ FlatSpecLike, Matchers, OneInstancePerTest }

/** Parent trait for unit tests in pgb. */
trait UnitSpec extends FlatSpecLike with Matchers with OneInstancePerTest
