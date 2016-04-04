package messaging

import java.time.Instant

import _root_.redis.clients.jedis.{JedisPool, JedisPubSub}
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.{ActorEventBus, LookupClassification}
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

sealed trait Protocol

case class Process(messageId: String, timesOutAt: Instant) extends Protocol

sealed trait MonitoringProtocol

case object MonitoringStarted extends MonitoringProtocol

case class ProcessingStarted(messageId: String, timesOutAt: Instant) extends MonitoringProtocol

case class ProcessingTimedOut(messageId: String, timedOutAt: Instant) extends MonitoringProtocol

case class ProcessingCompleted(messageId: String, completedAt: Instant) extends MonitoringProtocol


object RedisQueue {
  val NamePattern = "[A-Za-z0-9_-]+"
}

class RedisQueue(val name: String, val initialTimeout: FiniteDuration = 30 seconds)(process: String => Future[Unit])(implicit actorSystem: ActorSystem, val jedisPool: JedisPool)
  extends redis.RedisSupport {

  require(name matches RedisQueue.NamePattern, s"queue name incorrect: $name")

  val ControlChannel = s"$name:control"
  val IncomingQueueKey = s"$name:incoming"
  val PendingQueueKey = s"$name:pending"
  val ProcessingSortedSetKey = s"$name:processing"
  private val logger = Logger(classOf[RedisQueue])
  private implicit val dispatcher = actorSystem.dispatcher
  private val queueActor = actorSystem.actorOf(Props(new QueueActor), s"queue:$name")

  def enqueue(messageId: String) {
    withJedis {
      _.lpush(IncomingQueueKey, messageId)
    }
    ()
  }

  def startProcessing(): Unit = queueActor ! 'startProcessing

  def stopProcessing(): Unit = queueActor ! 'stopProcessing

  def close(): Unit = actorSystem.stop(queueActor)

  private class ProcessingActor extends Actor {
    var processing = false

    override def receive = {
      case 'startProcessing =>
        processing = true
        self ! 'listen
      case 'stopProcessing =>
        processing = false
      case 'listen =>
        if (processing) {
          val messageId = Option(withJedis {
            _.brpoplpush(IncomingQueueKey, PendingQueueKey, 1)
          })

          val timesOutAt = Instant.now() plusMillis initialTimeout.toMillis
          ProcessPendingScript(timesOutAt)

          messageId.foreach { messageId =>
            logger.debug(s"Processing $messageId from $IncomingQueueKey")
            process(messageId).onComplete { result =>
              CompletedScript(Instant.now(), messageId)
              logger.debug(s"Completed $messageId with $result")
            }
          }
          self ! 'listen
        }
    }
  }

  private class MonitoringActor extends Actor {
    val subscription = Future[Unit] {
      subscribeToChannels(ControlChannel)(ControlChannelSubscriber)
    }
    subscription.onFailure {
      case throwable => self ! throwable
    }

    override def receive = {
      case throwable: Throwable => throw throwable
    }

    override def postStop {
      ControlChannelSubscriber.unsubscribe(ControlChannel)
      Await.ready(subscription, 5 seconds)
      ()
    }

    object ControlChannelSubscriber extends JedisPubSub with RedisPubSubNoOps {
      private val Started = s"STARTED (${RedisQueue.NamePattern}) ([0-9]+)".r
      private val Completed = s"COMPLETED (${RedisQueue.NamePattern}) ([0-9]+)".r
      private val TimedOut = s"TIMEDOUT (${RedisQueue.NamePattern}) ([0-9]+)".r

      override def onSubscribe(channel: String, subscribedChannels: Int) {
        monitoring publish MonitoringStarted
      }

      override def onMessage(channel: String, message: String) = monitoring publish (message match {
        case Started(messageId, timesOutAt) =>
          ProcessingStarted(messageId, Instant.ofEpochMilli(timesOutAt.toLong))
        case Completed(messageId, completedAt) =>
          ProcessingCompleted(messageId, Instant.ofEpochMilli(completedAt.toLong))
        case TimedOut(messageId, timedOutAt) =>
          ProcessingTimedOut(messageId, Instant.ofEpochMilli(timedOutAt.toLong))
      })
    }

  }

  private class TimeoutActor extends Actor {
    val timeoutChecker = context.system.scheduler.schedule(
      initialDelay = 1 seconds, interval = (10 seconds) min initialTimeout,
      receiver = self, message = 'checkTimeout)

    override def receive = {
      case 'checkTimeout => TimedOutScript(Instant.now())
    }

    override def postStop = timeoutChecker.cancel()
  }

  private class QueueActor extends Actor {
    val processingActor = context.actorOf(Props(new ProcessingActor), "processing")
    val subscriptionActor = context.actorOf(Props(new MonitoringActor), "monitoring")
    val timeoutActor = context.actorOf(Props(new TimeoutActor), "timeout")

    override def receive = {
      case message@('startProcessing | 'stopProcessing) =>
        processingActor ! message
    }
  }

  object monitoring extends ActorEventBus with LookupClassification {
    type Classifier = Unit
    type Event = MonitoringProtocol

    def subscribe(subscriber: Subscriber): Boolean = subscribe(subscriber, ())

    override protected def classify(event: Event) = ()

    override protected def mapSize(): Int = 1

    override protected def publish(event: Event, subscriber: Subscriber) = subscriber ! event
  }

  private object ProcessPendingScript extends LuaScript(
    """
      | local pendingQueueKey = KEYS[1]
      | local processingSortedSetKey = KEYS[2]
      | local controlChannel = KEYS[3]
      | local initialTimeout = ARGV[1]
      | for _, messageId in ipairs(redis.call('lrange', pendingQueueKey, 0, -1)) do
      |   redis.call('zadd', processingSortedSetKey, initialTimeout, messageId)
      |   redis.call('publish', controlChannel, 'STARTED ' .. messageId .. ' ' .. initialTimeout)
      | end
      | redis.call('del', pendingQueueKey)
    """.stripMargin) {
    def apply(initialTimeout: Instant) {
      eval(PendingQueueKey, ProcessingSortedSetKey, ControlChannel)(initialTimeout.toEpochMilli.toString)
      ()
    }
  }

  private object TimedOutScript extends LuaScript(
    """
      | local processingSortedSetKey = KEYS[1]
      | local controlChannel = KEYS[2]
      | local now = ARGV[1]
      | for _, messageId in ipairs(redis.call('zrangebyscore', processingSortedSetKey, 0, now)) do
      |   redis.call('zrem', processingSortedSetKey, messageId)
      |   redis.call('publish', controlChannel, 'TIMEDOUT ' .. messageId .. ' ' .. now)
      | end
    """.stripMargin) {
    def apply(now: Instant) {
      eval(ProcessingSortedSetKey, ControlChannel)(now.toEpochMilli.toString)
      ()
    }
  }

  private object CompletedScript extends LuaScript(
    """
      | local processingSortedSetKey = KEYS[1]
      | local controlChannel = KEYS[2]
      | local now = ARGV[1]
      | local messageId = ARGV[2]
      | if redis.call('zrem', processingSortedSetKey, messageId) > 0 then
      |   redis.call('publish', controlChannel, 'COMPLETED ' .. messageId .. ' ' .. now)
      | end
    """.stripMargin) {
    def apply(now: Instant, messageId: String) {
      eval(ProcessingSortedSetKey, ControlChannel)(now.toEpochMilli.toString, messageId)
      ()
    }
  }

}
