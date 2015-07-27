package skarn.push

import java.io.FileInputStream
import scala.util.Try

/**
 * Created by yusuke on 15/07/08.
 */
object Apns {
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
