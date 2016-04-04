package dao.anorm

import java.math.{BigDecimal => JBigDec, BigInteger}

import anorm.SqlParser.get
import anorm._
import eventstore.StoreRevision
import play.api.libs.json.{JsValue, Json}

trait Implicits {

  // StoreRevision
  implicit object storeRevisionToStatement extends ToStatement[StoreRevision] {
    override def set(s: java.sql.PreparedStatement, index: Int, v: StoreRevision): Unit = s.setLong(index, v.value)
  }

  implicit val columnToStoreRevision: Column[StoreRevision] = Column.nonNull1 { (value, meta) =>
    val MetaDataItem(qualified, _, _) = meta
    value match {
      case bi: BigInteger => Right(StoreRevision(bi.longValue))
      case bd: JBigDec => Right(StoreRevision(bd.longValue))
      case int: Int => Right(StoreRevision(int: Long))
      case long: Long => Right(StoreRevision(long))
      case s: Short => Right(StoreRevision(s.toLong))
      case b: Byte => Right(StoreRevision(b.toLong))
      case bool: Boolean => Right(StoreRevision(if (!bool) 0l else 1l))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to StoreRevision for column $qualified"))
    }
  }

  // JsValue
  implicit object jsonToStatement extends ToStatement[JsValue] {
    override def set(s: java.sql.PreparedStatement, index: Int, v: JsValue): Unit = {
      val jsonObject = new org.postgresql.util.PGobject()
      jsonObject.setType("json")
      jsonObject.setValue(Json.stringify(v))
      s.setObject(index, jsonObject)
    }
  }

  implicit val columnToJson: Column[JsValue] = Column.nonNull1 { (value, meta) =>
    val MetaDataItem(qualified, _, _) = meta
    value match {
      case json: org.postgresql.util.PGobject => Right(Json.parse(json.getValue))
      case json: String => Right(Json.parse(json))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Json for column $qualified"))
    }
  }

  protected[this] def json(name: String): RowParser[JsValue] = get[JsValue](name)
}
