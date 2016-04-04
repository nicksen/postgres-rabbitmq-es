package dao

import eventstore.{Commit, StoreRevision}
import play.api.libs.json.JsValue

trait CommitDAO[Event] {

  def create(commitId: StoreRevision, commit: Commit[Event]): Unit

  def get(commitIds: Seq[Long]): Seq[JsValue]

  def length: Long
}
