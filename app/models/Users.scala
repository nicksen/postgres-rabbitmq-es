package models

import domain.{EmailAddress, Password, Token}
import events._
import eventstore.StreamRevision
import play.api.libs.json.{Format, Json}

trait CurrentUserContext {

  /**
    * The current authenticated user or the guest user.
    */
  def currentUser: User
}

trait UsersContext {

  /**
    * All known users and sessions.
    */
  def users: Users
}


sealed trait User {

  def registered: Option[RegisteredUser] = None

  def authorizeEvent(state: ApplicationState): (DomainEvent) => Boolean = {
    case event: UserRegistered => true
    case _ => false
  }
}

case class UnknownUser(id: Option[UserId] = None) extends User

case class RegisteredUser(id: UserId, rev: StreamRevision, emailAddress: EmailAddress, password: Password) extends User {

  override def registered: Option[RegisteredUser] = Some(this)

  override def authorizeEvent(state: ApplicationState): (DomainEvent) => Boolean = {
    case event: UserRegistered => false
    case event: UserProfileChanged => true
    case event: UserEmailAddressChanged => true
    case event: UserPasswordChanged => true
    case event: UserLoggedIn => true
    case event: UserLoggedOut => true
    case _ => false
  }
}

object RegisteredUser {
  implicit val fmt: Format[RegisteredUser] = Json.format[RegisteredUser]
}

/**
  * Memory image of all registered users based on (replaying) the user events.
  */
case class Users(private val byId: Map[UserId, RegisteredUser] = Map.empty) {

  def get(id: UserId): Option[RegisteredUser] = byId.get(id)

  def get(token: Token): Option[RegisteredUser] = None

  def get = byId.values.toSeq

  def update(event: UserEvent, rev: StreamRevision): Users = event match {
    case UserRegistered(userId, email, password) =>
      val user = RegisteredUser(userId, rev, email, password)
      copy(byId = byId.updated(userId, user))

    case UserProfileChanged(userId) => this

    case UserEmailAddressChanged(userId, email) =>
      val user = byId(userId)
      copy(byId = byId.updated(userId, user.copy(rev = rev, emailAddress = email)))

    case UserPasswordChanged(userId, password) =>
      val user = byId(userId)
      copy(byId = byId.updated(userId, user.copy(rev = rev, password = password)))

    case UserLoggedIn(userId, token) => this

    case UserLoggedOut(userId) => this
  }
}
