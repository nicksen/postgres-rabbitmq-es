package support

import java.net.URLDecoder
import java.util.UUID

import domain.{EmailAddress, Identifier, IdentifierCompanion}
import play.api.mvc.PathBindable

object Binders {

  private[this] val charset: String = "utf-8"

  implicit def identifierPathBindable[A <: Identifier](implicit companion: IdentifierCompanion[A]) = new PathBindable[A] {
    override def bind(key: String, value: String) = try {
      Right(companion.apply(UUID.fromString(decode(value))))
    } catch {
      case _: RuntimeException => Left("Cannot parse parameter %s as %s: %s".format(key, companion, decode(value)))
    }

    override def unbind(key: String, value: A) = value.uuid.toString
  }

  private[this] def decode(value: String): String = URLDecoder.decode(value, charset)

  implicit object pathBindableEmailAddress extends PathBindable[EmailAddress] {
    override def bind(key: String, value: String): Either[String, EmailAddress] = try {
      Right(EmailAddress(decode(value)))
    } catch {
      case _: RuntimeException => Left("Cannot parse parameter %s as EmailAddress: %s".format(key, decode(value)))
    }

    override def unbind(key: String, value: EmailAddress): String = value.toString
  }

}
