package dao.anorm

import anorm.SQL
import anorm.SqlParser.{long, str}
import dao.CommitDAO
import eventstore.{Commit, StoreRevision}
import play.api.db.Database
import play.api.libs.json.{JsValue, Format, Json}

import scala.language.postfixOps

class CommitDAOImpl[Event: Format](db: Database) extends CommitDAO[Event] with Implicits {

  override def create(commitId: StoreRevision, commit: Commit[Event]): Unit = db.withConnection { implicit c =>
    SQL("INSERT INTO commits (store_revision, data) VALUES ({commitId}, {data})")
      .on(
        'commitId -> commitId,
        'data -> Json.toJson(commit))
      .executeUpdate()
  }

  override def get(commitIds: Seq[Long]): Seq[JsValue] = db.withConnection { implicit c =>
    SQL("SELECT data AS d FROM commits WHERE store_revision IN ({commitIds})")
      .on('commitIds -> commitIds)
      .as(json("d") *)
  }

  override def length: Long = db.withConnection { implicit c =>
    SQL("SELECT COUNT(*) AS c FROM commits")
      .as(long("c") single)
  }
}
