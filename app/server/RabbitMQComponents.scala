package server

import akka.actor.{ActorRef, ActorSystem}
import com.thenewmotion.akka.rabbitmq.{ConnectionActor, ConnectionFactory}

import scala.concurrent.duration._
import scala.language.postfixOps

trait RabbitMQComponents {

  lazy val connectionFactory: ConnectionFactory = new ConnectionFactory
  lazy val connection: ActorRef =
    actorSystem.actorOf(ConnectionActor.props(connectionFactory, reconnectionDelay = 10 seconds), "rabbitmq")

  def actorSystem: ActorSystem
}
