/**
 * Created by yusuke on 2015/01/23.
 * reference: https://github.com/spray/spray/blob/release/1.2/spray-client/src/test/scala/spray/client/HttpHostConnectorSpec.scala
 */
package skarn

import akka.actor.ActorSystem
import org.scalatest.{ Suite, BeforeAndAfterAll }
import spray.util.Utils

import scala.concurrent.duration._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory


trait TemporaryServer extends BeforeAndAfterAll { this: Suite =>
  implicit val timeout: Timeout = 2 seconds
  val testConf = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    akka.io.tcp.trace-logging = off
    spray.can.host-connector.max-retries = 4
    spray.can.client.request-timeout = 400ms
    spray.can.client.user-agent-header = "RequestMachine"
    spray.can.client.proxy.http = none"""
  )
  val serverSystem = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)

  val serverDispatcher = serverSystem.dispatcher

  val (serverInterface, serverPort) = Utils.temporaryServerHostnameAndPort()

  override protected def afterAll() {
    super.afterAll()
    serverSystem.shutdown()
    serverSystem.awaitTermination()
  }
}
