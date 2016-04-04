package dao.anorm

import anorm.SQL
import anorm.SqlParser.long
import dao.StreamDAO
import eventstore.StoreRevision
import play.api.db.Database

import scala.language.postfixOps

class StreamDAOImpl(db: Database) extends StreamDAO with Implicits {

  override def add(streamId: String, commitId: StoreRevision): Unit = db.withConnection { implicit c =>
    SQL("INSERT INTO streams (stream_id, commit_id) VALUES ({streamId}, {commitId})")
      .on(
        'streamId -> streamId,
        'commitId -> commitId)
      .executeUpdate()
  }

  override def commitIds(streamId: String, from: Long, to: Long): Seq[Long] = db.withConnection { implicit c =>
    SQL("SELECT commit_id AS c FROM streams WHERE stream_id = {streamId} LIMIT {from} OFFSET {max}")
      .on(
        'streamId -> streamId,
        'from -> from,
        'max -> (to - from))
      .as(long("c") *)
  }

  override def length(streamId: String): Long = db.withConnection { implicit c =>
    SQL("SELECT COUNT(*) AS c FROM streams WHERE stream_id = {streamId}")
      .on('streamId -> streamId)
      .as(long("c") single)
  }
}
