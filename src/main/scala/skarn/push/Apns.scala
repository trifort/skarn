package skarn.push

import java.io.FileInputStream
import java.nio.ByteOrder
import akka.util.ByteString
import scala.util.Try
import com.notnoop.apns.internal.Utilities

/**
 * Created by yusuke on 15/07/08.
 */
object Apns {
  sealed trait FrameItem {
    val id: Byte
    val data: ByteString
    def serialize: ByteString = {
      implicit val order = ByteOrder.BIG_ENDIAN
      ByteString.newBuilder
        .putByte(id)
        .putShort(data.length)
        .result() ++ data
    }
  }

  case class DeviceToken(token: String) extends FrameItem {
    val id: Byte = 1
    val data = ByteString(Utilities.decodeHex(token))
  }

  case class Payload(payload: String) extends FrameItem {
    val id: Byte = 2
    val data = ByteString(payload)
  }

  case class Identifier(unique: Int) extends FrameItem {
    val id: Byte = 3
    val data = {
      implicit val order = ByteOrder.BIG_ENDIAN
      ByteString.newBuilder.putInt(unique).result()
    }
  }

  case class FrameData(items: Seq[FrameItem]) {
    val command: Byte = 2
    def serialize = {
      implicit val order = ByteOrder.BIG_ENDIAN
      val frameData = items.map(_.serialize).reduce(_ ++ _)
      ByteString.newBuilder
        .putByte(command)
        .putInt(frameData.length)
        .result() ++ frameData
    }
  }

  def loadCertificateFromFile(path: String) = {
    /* pemをp12に変換
     * openssl pkcs12 -export -inkey apns.pem -in apns.pem -out apns.p12
     * reference: http://stackoverflow.com/questions/22525388/push-notification-caused-by-java-io-ioexception-toderinputstream-rejects-tag
    */
    Try(new FileInputStream(path))
  }

  def loadCertificateFromClassPath(path: String) = {
    Try(getClass.getClassLoader.getResource(path).openStream())
  }
}
