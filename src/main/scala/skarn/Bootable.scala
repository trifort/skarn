package skarn

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import skarn.push.PushServiceInfo
import kamon.Kamon
import java.io.File
import scala.collection.JavaConversions._

/**
 * Created by yusuke on 15/06/16.
 */
trait Bootable {
  val serviceBootstrap: ServiceBootstrap

  def startup() = {
    Kamon.start()
    val config = serviceBootstrap.config
    println(s"Config file: ${System.getProperty("CONFIG_PATH")}")
    println("Loaded services: ")
    PushServiceInfo.services.map(_.name).foreach(println)
    val system = ActorSystem(ProjectInfo.name, config)
    serviceBootstrap.run(system)
  }
}
