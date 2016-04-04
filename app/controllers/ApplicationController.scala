package controllers

import domain.{EmailAddress, Password}
import events.{UserEvent, UserId, UserRegistered}
import eventstore.Transaction._
import eventstore.{Changes, StreamRevision}
import models.Users
import play.api.libs.json.Json

class ApplicationController(actions: ControllerActions[Users, UserEvent]) {

  import actions._

  def index = QueryAction { state => implicit request =>
    Ok(Json.toJson(state.get))
  }

  def register = CommandAction { state => implicit request =>
    Changes(
      StreamRevision.Initial,
      UserRegistered(UserId.generate(), EmailAddress("nicklas@nicks.se"), Password.fromPlainText("hejhej")): UserEvent)
      .commit(onCommit = Ok, onConflict = _ => BadRequest)
  }
}
