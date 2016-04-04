package domain

import play.api.libs.json.Format
import support.JsonMapping._

import scala.util.matching.Regex

/**
  * Tokens used to track users. Represented as 128-bit randomly generated numbers.
  */
case class Token private(a: Long, b: Long) {
  override def toString = f"$a%016x-$b%016x"
}

object Token {
  implicit val fmt: Format[Token] = valueFormat(apply)(_.toString)

  private[this] val pattern: Regex = """\b([0-9a-fA-F]{16})-([0-9a-fA-F]{16})\b""".r

  private[this] val rand: java.util.Random = new java.security.SecureRandom()

  def generate(): Token = Token(rand.nextLong, rand.nextLong)

  def apply(value: String): Token = fromString(value) getOrElse {
    throw new IllegalArgumentException(s"invalid authentication token '$value'")
  }

  def fromString(s: String): Option[Token] = s match {
    case pattern(a, b) => Some(Token(BigInt(a, 16).longValue(), BigInt(b, 16).longValue()))
    case _ => None
  }
}
