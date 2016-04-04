package domain

import com.lambdaworks.crypto.SCryptUtil
import play.api.libs.json.Format
import support.JsonMapping._

/**
  * Hashed passwords that avoid leaking the contents when converted to strings.
  */
case class Password private(hash: String) {
  require(hash.startsWith("$s0$"), "invalid password hash")

  def verify(password: String): Boolean = SCryptUtil.check(password, hash)

  override def toString = "********"
}

object Password {
  implicit val fmt: Format[Password] = valueFormat(fromHash)(_.hash)
  private[this] val N: Int = 1 << 14
  private[this] val r: Int = 8
  private[this] val p: Int = 2

  def fromHash(hash: String): Password = Password(hash)

  def fromPlainText(password: String): Password = Password(SCryptUtil.scrypt(password, N, r, p))
}
