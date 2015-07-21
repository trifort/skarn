package skarn.push

import java.io.File

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory, Config}
import scala.collection.JavaConversions._

/**
 * Created by yusuke on 15/07/07.
 */

case class APNSInfo(certPath: String, password: String)
case class GCMInfo(apiKey: String)
case class PushService(name: String, authToken: String, apns: APNSInfo, gcm: GCMInfo)

trait PushServiceInfo {
  val config: Config
  lazy val services = config.getConfigList("services").toList.map {c =>
    val serviceName = c.getString("name")
    val authToken = c.getString("auth-token")
    val apnsCertPath = c.getString("apns.cert-path")
    val apnsPass = c.getString("apns.password")
    val gcmApiKey = c.getString("gcm.api-key")
    PushService(serviceName, authToken, APNSInfo(apnsCertPath, apnsPass), GCMInfo(gcmApiKey))
  }

  def findByName(serviceName: String): Option[PushService] = services.find(_.name == serviceName)

  def findByToken(authToken: String): Option[PushService] = services.find(_.authToken == authToken)
}

object PushServiceInfo extends PushServiceInfo {
  val config: Config = ConfigFactory.parseFile(new File(System.getProperty("CONFIG_PATH"))).resolveWith(ConfigFactory.defaultOverrides)
}