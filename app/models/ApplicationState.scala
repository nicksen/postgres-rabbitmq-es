package models

import events.{ClubEvent, DomainEvent, UserEvent}
import eventstore.StreamRevision

case class ApplicationState(clubs: Clubs = Clubs(), users: Users = Users()) {

  def updateMany(events: Seq[(DomainEvent, StreamRevision)]): ApplicationState = events.foldLeft(this) {
    case (state, (event, rev)) => state.update(event, rev)
  }

  def update(event: DomainEvent, rev: StreamRevision): ApplicationState = event match {
    case event: ClubEvent => copy(clubs = clubs.update(event, rev))
    case event: UserEvent => copy(users = users.update(event, rev))
    case _ => sys.error(s"unknown event: $event")
  }
}
