package server

import controllers.{ApplicationController, Assets, MemoryImageActions}
import events.DomainEvent
import play.api.http.HttpErrorHandler

trait ControllerComponents {

  lazy val applicationController: ApplicationController = new ApplicationController(memoryImageActions.view(_.users))

  lazy val assets: Assets = new Assets(httpErrorHandler)

  def memoryImageActions: MemoryImageActions[DomainEvent]

  def httpErrorHandler: HttpErrorHandler
}
