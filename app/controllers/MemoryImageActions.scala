package controllers

import domain.Token
import events.DomainEvent
import eventstore.{MemoryImage, Transaction}
import models.{ApplicationState, UnknownUser, User}
import play.api.mvc.{Action, AnyContent, Request}

/**
  * Implements `ControllerActions` using the (global) memory image containing the
  * `ApplicationState`.
  */
class MemoryImageActions[Event <: DomainEvent](memoryImage: MemoryImage[ApplicationState, Event])
  extends ControllerActions[ApplicationState, Event] {

  override def QueryAction(block: QueryBlock[AnyContent]) = Action { request =>
    val state = memoryImage.get
    block(state)(buildApplicationRequest(request, state))
  }

  private def buildApplicationRequest[A](request: Request[A], state: ApplicationState) = {
    val currentUser: User = request.headers.get("Authorization")
      .flatMap(Token.fromString)
      .flatMap(state.users.get)
      .getOrElse(UnknownUser())
    new ApplicationRequest(request, currentUser, state.users)
  }

  override def CommandAction(block: CommandBlock[AnyContent]) = Action { request =>
    memoryImage.modify { state =>
      val applicationRequest = buildApplicationRequest(request, state)
      val currentUser = applicationRequest.currentUser
      val transaction = block(state)(applicationRequest)
      if (transaction.events.forall(currentUser.authorizeEvent(state))) {
        transaction.withHeaders(currentUser.registered.map(user => "currentUserId" -> user.id.toString).toSeq: _*)
      } else {
        Transaction.abort(notFound(request))
      }
    }
  }
}
