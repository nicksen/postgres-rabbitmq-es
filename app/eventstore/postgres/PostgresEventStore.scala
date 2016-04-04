package eventstore.postgres

import java.nio.charset.Charset
import java.time.Instant
import java.util.UUID
import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRef, ActorSystem}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Envelope
import com.thenewmotion.akka.rabbitmq.{Channel, DefaultConsumer}
import dao.{CommitDAO, StreamDAO}
import eventstore._
import play.api.Logger
import play.api.libs.json.{Format, JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

/**
  * The Postgres event store implementation uses two primary data structures:
  *
  * - A hash containing all commits indexed by the store revision (commit id).
  * - A list of commit ids per event stream.
  *
  * Events must have an associated `Format` instance to allow for (de)serialization to JSON.
  */
class PostgresEventStore[Event: Format](commitDAO: CommitDAO[Event],
                                        streamDAO: StreamDAO,
                                        val system: ActorSystem,
                                        val connection: ActorRef,
                                        name: String,
                                        val charset: Charset)
  extends EventStore[Event]
  with messaging.amqp.RabbitMQSupport {


  // Used as commits publication channel for subscribers.
  protected[this] val CommitsKey: String = s"$name:commits"

  // Executor for event store subscribers. Each subscriber gets its own thread.
  private[this] implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)

  // Subscriber control channel for handling event store closing and subscription cancellation.
  private[this] val ControlChannel: String = s"$name:control"

  // Maximum number of commits to read at one time.
  private[this] val ChunkSize = 10000

  // Control message used to notify subscribers the event store is closing.
  private[this] val CloseToken = UUID.randomUUID.toString
  @volatile private[this] var closed = false

  // Reads and deserializes commits in `ChunkSize` chunks.
  private[this] def doReadCommits(commitIds: Seq[Long]): Stream[Commit[Event]] = {
    val chunks = commitIds.grouped(ChunkSize)
    chunks.flatMap(commitDAO.get)
      .map(deserializeCommit)
      .toStream
  }

  // Helpers to serialize and deserialize commits.
  protected[this] def deserializeCommit(serialized: JsValue): Commit[Event] = Json.fromJson[Commit[Event]](serialized).get

  protected[this] def serializeCommit(commit: Commit[Event]): JsValue = Json.toJson(commit)

  object reader extends CommitReader[Event] {
    override def storeRev = StoreRevision(commitDAO.length)

    override def readCommits[E <: Event : Manifest](since: StoreRevision, to: StoreRevision): Stream[Commit[E]] = {
      val current = storeRev
      if (since >= current) Stream.empty
      else {
        val revisionRange = (since.value + 1) to (to.value min current.value)
        doReadCommits(revisionRange).map(_.withOnlyEventsOfType[E])
      }
    }

    override def streamRev[StreamId, E](streamId: StreamId)
                                       (implicit descriptor: EventStreamType[StreamId, E]): StreamRevision =
      StreamRevision(streamDAO.length(descriptor.toString(streamId)))

    override def readStream[StreamId, E <: Event](streamId: StreamId,
                                                  since: StreamRevision = StreamRevision.Initial,
                                                  to: StreamRevision = StreamRevision.Maximum)
                                                 (implicit descriptor: EventStreamType[StreamId, E]): Stream[Commit[E]] = {
      val commitIds = streamDAO.commitIds(descriptor.toString(streamId), since.value, to.value)
      doReadCommits(commitIds).map(commit => commit.copy(events = commit.events.map(descriptor.cast)))
    }
  }

  object committer extends EventCommitter[Event] {
    override def tryCommit[E <: Event](changes: Changes[E]): CommitResult[E] = {
      implicit val descriptor = changes.eventStreamType

      val timestamp = Instant.now().toEpochMilli
      val streamId = descriptor.toString(changes.streamId)
      val expected = changes.expected
      val events = changes.events
      val headers = changes.headers

      val actual: StreamRevision = reader.streamRev(changes.streamId)
      if (actual != expected) {
        val conflicting = reader.readStream(changes.streamId, since = changes.expected)
        Left(Conflict(conflicting.flatMap(_.committedEvents)))
      } else {
        val storeRev = reader.storeRev
        val commitId = storeRev + 1
        val commit = Commit(commitId, timestamp, streamId, expected.next, events, headers)
        commitDAO.create(commitId, commit)
        streamDAO.add(streamId, commitId)
        publish(Json.stringify(Json.obj("topic" -> CommitsKey, "commit" -> serializeCommit(commit))))
        Right(commit)
      }
    }
  }

  object publisher extends CommitPublisher[Event] {

    override def subscribe[E <: Event : Manifest](since: StoreRevision)(listener: CommitListener[E]): Subscription = {
      @volatile var cancelled = false
      @volatile var last = since
      val unsubscribeToken = UUID.randomUUID.toString

      def replayCommitsTo(to: StoreRevision) {
        if (last < to) {
          Logger.info(s"Replaying commits since $last to $to")
          reader.readCommits(last, to).takeWhile(_ => !closed && !cancelled).foreach(listener)
          last = to
        }
      }

      case class Subscriber(channel: Channel) extends DefaultConsumer(channel) {

        private[this] def unsubscribe(consumerTag: String) = channel.basicCancel(consumerTag)

        override def handleConsumeOk(consumerTag: String): Unit = {
          super.handleConsumeOk(consumerTag)
          // We may have missed some commits while subscribing, so replay missing if needed.
          replayCommitsTo(reader.storeRev)
        }

        override def handleDelivery(consumerTag: String,
                                    envelope: Envelope,
                                    properties: BasicProperties,
                                    body: Array[Byte]): Unit = {
          val message = Json.parse(body).as[JsObject]
          (message \ "topic").as[String] match {
            case ControlChannel =>
              val mess = (message \ "message").as[String]
              if (mess == CloseToken || mess == unsubscribeToken) unsubscribe(consumerTag)
              channel.basicAck(envelope.getDeliveryTag, false)
            case CommitsKey =>
              val commit = deserializeCommit((message \ "commit").get)
              if (last.next < commit.storeRev) {
                Logger.warn(s"missing commits since $last to ${commit.storeRev}, replaying...")
                replayCommitsTo(commit.storeRev)
              } else if (last.next == commit.storeRev) {
                listener(commit.withOnlyEventsOfType[E])
                last = commit.storeRev
              } else {
                Logger.warn(s"Ignoring old commit ${commit.storeRev}, since we already processed everything up to $last")
              }
              channel.basicAck(envelope.getDeliveryTag, false)
            case _ =>
              Logger.warn(s"message received on unknown channel '${message \ "topic"}'")
              channel.basicAck(envelope.getDeliveryTag, false)
          }
        }
      }

      val currentRevision = reader.storeRev
      if (last > currentRevision) {
        Logger.warn(s"Last $last is in the future, resetting it to current $currentRevision")
        last = currentRevision
      } else {
        replayCommitsTo(currentRevision)
      }

      Future[Unit] {
        subscribeToChannels(channel => Subscriber(channel))
      }

      new Subscription {
        override def cancel(): Unit = {
          cancelled = true
          publish(Json.stringify(Json.obj("topic" -> ControlChannel, "message" -> unsubscribeToken)))
        }

        override def toString: String = s"Subscription($last, $cancelled, ${PostgresEventStore.this})"
      }
    }
  }

  /**
    * Closes the event store. All subscribers will be unsubscribed and connections will be closed.
    */
  override def close(): Unit = {
    closed = true
    publish(Json.stringify(Json.obj("topic" -> ControlChannel, "message" -> CloseToken)))
    executionContext.shutdown()
    if (!executionContext.awaitTermination(5, TimeUnit.SECONDS)) {
      executionContext.shutdownNow
    }
    ()
  }
}
