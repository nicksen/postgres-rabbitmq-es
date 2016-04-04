package server

import dao.anorm.{CommitDAOImpl, StreamDAOImpl}
import dao.{CommitDAO, StreamDAO}
import events.DomainEvent
import play.api.db.Database
import play.api.libs.json.Format

trait DAOComponents {

  lazy val commitDAO: CommitDAO[DomainEvent] = new CommitDAOImpl[DomainEvent](db)
  lazy val streamDAO: StreamDAO = new StreamDAOImpl(db)

  def db: Database

  implicit def DomainEventFormat: Format[DomainEvent]
}
