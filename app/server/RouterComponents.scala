package server

import controllers.{ApplicationController, Assets}
import play.api.http.HttpErrorHandler
import play.api.routing.Router
import router.Routes

trait RouterComponents {

  lazy val router: Router = new Routes(httpErrorHandler, applicationController, assets)

  def httpErrorHandler: HttpErrorHandler

  def assets: Assets

  def applicationController: ApplicationController
}
