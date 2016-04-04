package dao

import eventstore.StoreRevision

trait StreamDAO {

  def add(streamId: String, commitId: StoreRevision): Unit

  def commitIds(streamId: String, from: Long, to: Long): Seq[Long]

  def length(streamId: String): Long
}
