package server

import java.nio.charset.{Charset, StandardCharsets}

import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.cache.EhCacheComponents
import play.api.db.evolutions.{DynamicEvolutions, EvolutionsComponents}
import play.api.db.{DBComponents, Database, HikariCPComponents}
import play.api.i18n.I18nComponents
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSComponents
import play.filters.gzip.GzipFilterComponents
import play.filters.headers.SecurityHeadersComponents

class ApplicationLoader extends play.api.ApplicationLoader {
  override def load(context: Context) = Components.from(context).application
}

class Components(context: Context)
  extends BuiltInComponentsFromContext(context)
  with HikariCPComponents
  with DBComponents
  with EvolutionsComponents
  with EhCacheComponents
  with GzipFilterComponents
  with SecurityHeadersComponents
  with CORSComponents
  with I18nComponents
  with MemoryImageComponents
  with DAOComponents
  with RabbitMQComponents
  with ControllerComponents
  with RouterComponents {

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(gzipFilter, securityHeadersFilter, corsFilter)

  override lazy val db: Database = dbApi.database("default")

  override lazy val dynamicEvolutions: DynamicEvolutions = new DynamicEvolutions

  override lazy val charset: Charset = StandardCharsets.UTF_8

  applicationEvolutions.start()

  initializeMemoryImage()
}

object Components {
  def from(context: Context) = new Components(context)
}
