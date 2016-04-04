package server

import java.nio.charset.Charset
import java.time.Instant

import akka.actor.{ActorRef, ActorSystem}
import controllers.MemoryImageActions
import dao.{CommitDAO, StreamDAO}
import events.{ClubEvent, DomainEvent, UserEvent}
import eventstore.{EventStore, _}
import models.ApplicationState
import play.api.libs.json.Format
import play.api.{Configuration, Logger}

trait MemoryImageComponents {

  lazy val memoryImageActions = new MemoryImageActions(memoryImage)

  private[this] lazy val memoryImage = MemoryImage[ApplicationState, DomainEvent](eventStore)(ApplicationState()) {
    (state, commit) => state.updateMany(commit.eventsWithRevision)
  }

  private[this] lazy val config = configuration.getConfig("eventstore")
    .getOrElse(throw configuration.globalError("missing [eventstore] configuration"))

  private[this] lazy val eventStore: EventStore[DomainEvent] =
    config.getString("implementation", Some(Set("fake", "postgres"))).get match {
      case "fake" => new fake.FakeEventStore[DomainEvent]
      case "postgres" =>
        new postgres.PostgresEventStore[DomainEvent](commitDAO, streamDAO, actorSystem, connection, "golf", charset)
    }

  implicit val DomainEventFormat: Format[DomainEvent] = ClubEvent.JsonFormat and UserEvent.JsonFormat

  def configuration: Configuration

  def commitDAO: CommitDAO[DomainEvent]

  def streamDAO: StreamDAO

  def connection: ActorRef

  def charset: Charset

  implicit def actorSystem: ActorSystem

  protected[this] def initializeMemoryImage() {
    val start: Long = Instant.now().toEpochMilli

    val commitCount: Long = eventStore.reader.storeRev.value
    memoryImage.get // Waits for event replay to complete.

    val stop: Long = Instant.now().toEpochMilli
    val elapsed: Double = (stop - start) / 1000.0
    Logger.info("Loaded memory image with %d commits in %.3f seconds (%.1f commits/second)" format
      (commitCount, elapsed, commitCount / elapsed))
  }
}
