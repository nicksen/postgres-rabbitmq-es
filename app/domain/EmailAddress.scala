package domain

import play.api.libs.json.Format
import support.JsonMapping._

import scala.util.control.Exception.catching

case class EmailAddress(value: String) {
  // Pattern copied from `play.api.data.validation.Constraints`.
  require(
    value matches """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""",
    s"invalid email address: $value")

  override def toString = value
}

object EmailAddress {
  implicit val fmt: Format[EmailAddress] = valueFormat(apply)(_.toString)

  def fromString(s: String): Option[EmailAddress] = catching(classOf[IllegalArgumentException]) opt EmailAddress(s)
}
