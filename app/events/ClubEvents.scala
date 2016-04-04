package events

import java.util.UUID

import domain.{Identifier, IdentifierCompanion}
import eventstore.{ConflictsWith, EventStreamType}
import play.api.libs.json.Json
import support.JsonMapping.TypeChoiceFormat

/**
  * Strongly typed identifiers for clubs
  */
case class ClubId(uuid: UUID) extends Identifier

object ClubId extends IdentifierCompanion[ClubId]("ClubId")


sealed trait ClubEvent extends DomainEvent {
  def clubId: ClubId
}

case class ClubAdded(clubId: ClubId, name: String) extends ClubEvent

case class ClubEdited(clubId: ClubId, name: String) extends ClubEvent

case class ClubDeleted(clubId: ClubId) extends ClubEvent

object ClubEvent {
  implicit val cw: ConflictsWith[ClubEvent] = ConflictsWith {
    case _ => false
  }

  implicit val est: EventStreamType[ClubId, ClubEvent] = EventStreamType(_.toString, _.clubId)

  implicit val JsonFormat: TypeChoiceFormat[ClubEvent] = TypeChoiceFormat(
    "ClubAdded" -> Json.format[ClubAdded],
    "ClubEdited" -> Json.format[ClubEdited],
    "ClubDeleted" -> Json.format[ClubDeleted])
}
