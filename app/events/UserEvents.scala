package events

import java.util.UUID

import domain._
import eventstore.{ConflictsWith, EventStreamType}
import play.api.libs.json.Json
import support.JsonMapping.TypeChoiceFormat

/**
  * Strongly typed identifier for users.
  */
case class UserId(uuid: UUID) extends Identifier

object UserId extends IdentifierCompanion[UserId]("UserId")


/**
  * User events.
  */
sealed trait UserEvent extends DomainEvent {
  def userId: UserId
}

case class UserRegistered(userId: UserId, email: EmailAddress, password: Password) extends UserEvent

case class UserProfileChanged(userId: UserId) extends UserEvent

case class UserEmailAddressChanged(userId: UserId, email: EmailAddress) extends UserEvent

case class UserPasswordChanged(userId: UserId, password: Password) extends UserEvent

case class UserLoggedIn(userId: UserId, token: Token) extends UserEvent

case class UserLoggedOut(userId: UserId) extends UserEvent

object UserEvent {
  implicit val est: EventStreamType[UserId, UserEvent] = EventStreamType(_.toString, _.userId)
  implicit val cw: ConflictsWith[UserEvent] = ConflictsWith {
    case (_: UserRegistered, _) => true
    case _ => false
  }

  implicit val JsonFormat: TypeChoiceFormat[UserEvent] = TypeChoiceFormat(
    "UserRegistered" -> Json.format[UserRegistered],
    "UserProfileChanged" -> Json.format[UserProfileChanged],
    "UserEmailAddressChanged" -> Json.format[UserEmailAddressChanged],
    "UserPasswordChanged" -> Json.format[UserPasswordChanged],
    "UserLoggedIn" -> Json.format[UserLoggedIn],
    "UserLoggedOut" -> Json.format[UserLoggedOut])
}
