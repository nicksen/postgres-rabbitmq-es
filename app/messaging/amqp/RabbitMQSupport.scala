package messaging.amqp

import java.nio.charset.Charset

import akka.actor.{ActorRef, ActorSystem}
import com.rabbitmq.client.Consumer
import com.thenewmotion.akka.rabbitmq.{Channel, ChannelActor, ChannelMessage, CreateChannel}
import play.api.Logger

import scala.concurrent.blocking

trait RabbitMQSupport {

  private[this] val exchange = "amq.fanout"

  private[this] def setupPublisher(channel: Channel, self: ActorRef): Unit = {
    val queue = channel.queueDeclare.getQueue
    channel.queueBind(queue, exchange, "")
  }

  connection ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))

  protected[this] def subscribeToChannels(subscriber: (Channel) => Consumer): Unit = blocking {
    def setupSubscriber(channel: Channel, self: ActorRef): Unit = {
      val queue = channel.queueDeclare.getQueue
      channel.queueBind(queue, exchange, "")
      channel.basicConsume(queue, false, subscriber(channel))
    }

    connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))
  }

  protected[this] def publish(message: String): Unit = {
    val publisher = system.actorSelection("/user/rabbitmq/publisher")

    def publish(channel: Channel): Unit = {
      Logger.info(s"Publishing: $message")
      channel.basicPublish(exchange, "", null, toBytes(message))
    }
    publisher ! ChannelMessage(publish, dropIfNoChannel = false)
  }

  def system: ActorSystem

  def connection: ActorRef

  def charset: Charset

  protected[this] def fromBytes(x: Array[Byte]): String = new String(x, charset)

  protected[this] def toBytes(x: Any): Array[Byte] = x.toString.getBytes(charset)
}
