/**
 * Created by yusuke on 2015/02/07.
 */
package skarn

import com.typesafe.config.ConfigFactory

object ProjectInfo {
  val config = ConfigFactory.load("project").withFallback(ConfigFactory.load())
  val name = config.getString("name")
  val version = Version.apply(config.getString("version"))
  val ip = {
    val ip = config.getString("http.host")
    if (ip.contains("/")) {
      ip.replaceAll("""/\d+""", "")
    } else {
      ip
    }
  }
  val port = config.getInt("tcp.port")
  val httpPort = config.getInt("http.port")
  val path = s"akka.tcp://$name@$ip:$port"
}

case class Version(major: Int, minor: Int, suffix: Option[String]) {
  override def toString = suffix match {
    case Some(s) => s"$major.$minor-$s"
    case None => s"$major.$minor"
  }
}

object Version {
  def apply(version: String) = {
    parse(version).get
  }
  def parse(version: String): Option[Version] = {
    val Development = """(\d+)\.(\d+)-(.+)""".r
    val Production = """(\d+)\.(\d+)""".r
    version match {
      case Development(major, minor, suffix) =>
        val v = new Version(major.toInt, minor.toInt, Some(suffix))
        Some(v)
      case Production(major, minor) =>
        val v = new Version(major.toInt, minor.toInt, None)
        Some(v)
      case _ => None
    }
  }
}
