package models

import events._
import eventstore.StreamRevision

case class Club(clubId: ClubId, rev: StreamRevision, name: String)


case class Clubs(private val byId: Map[ClubId, Club] = Map.empty) {

  def get(id: ClubId): Option[Club] = byId.get(id)

  def update(event: ClubEvent, rev: StreamRevision): Clubs = event match {
    case ClubAdded(clubId, name) =>
      copy(byId = byId.updated(clubId, Club(clubId, rev, name)))

    case ClubEdited(clubId, name) =>
      val club = byId(clubId)
      copy(byId = byId.updated(clubId, club.copy(rev = rev, name = name)))

    case ClubDeleted(clubId) =>
      copy(byId = byId - clubId)
  }
}
