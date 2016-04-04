package domain

import java.util.UUID
import java.util.regex.Pattern

import play.api.libs.json.Format
import support.JsonMapping._

import scala.util.control.Exception.catching
import scala.util.matching.Regex

trait Identifier {
  def uuid: UUID
}

abstract class IdentifierCompanion[A <: Identifier](val prefix: String) {

  implicit val fmt: Format[A] = valueFormat(apply)(_.uuid)
  implicit val companion: IdentifierCompanion[A] = this
  private[this] val pattern: Regex = (Pattern.quote(prefix) + """\(([a-fA-F0-9-]{36})\)""").r

  def apply(uuid: UUID): A

  def generate(): A = apply(UUID.randomUUID)

  def fromString(s: String): Option[A] = s match {
    case pattern(uuid) => catching(classOf[RuntimeException]) opt apply(UUID.fromString(s))
    case _ => None
  }
}
