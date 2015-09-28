package skarn.apns

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Tcp, Flow}
import akka.testkit.TestKit
import akka.util.ByteString
import kamon.Kamon
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.push.{Apns, ApnsService}
import skarn.{StopSystemAfterAllWithAwaitTermination, TcpEncryptedServerTestSetupBase}
import collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future

/**
 * Created by yusuke on 15/09/17.
 */
class ApnsPushStreamProviderITest extends TestKit({Kamon.start(); ActorSystem("ApnsPushProviderITest")}) with TcpEncryptedServerTestSetupBase
with WordSpecLike with MustMatchers with StopSystemAfterAllWithAwaitTermination { testSelf =>

  implicit val materializer = ActorMaterializer()(testSelf.system)

  val testFlow = Flow[ByteString].map(v => {println("server received data", v); v})
  val probe = testActor

  val apnsService = new ApnsService {
    val certificate = Apns.loadCertificateFromClassPath("server_keystore").get
    val password = "passwd"
  }

  val sslContext = apnsService.sslContext

  var serverBinding: Tcp.ServerBinding = _

  override def beforeAll() = {
    super.beforeAll()
    val tcpserver = bindAndHandle(testFlow, "localhost", 2195)
    serverBinding = Await.result(tcpserver, 5 seconds)
  }

  override def afterAll() = {
    Await.result(serverBinding.unbind(), 5 seconds)
    super.afterAll()
  }

  "ApnsPushStreamProvider" must {
    "send multiple data with connection pool" in {
      import materializer.executionContext
      import ConnectionPool.Implicits._
      object ApnsPushStreamProvider extends ApnsPushStreamProvider {
        val connectionPool = ConnectionPool.create(TlsConnectionPoolSettings(serverBinding.localAddress, 10, 4, 5 seconds, apnsService.sslContext))
        connectionPool.startConnection()
        val maxRequest = 10
        val service = apnsService
      }

      val r1 = ApnsPushStreamProvider.send(Seq.fill(20)(ByteString("abcde")))
      val r2 = ApnsPushStreamProvider.send(Seq.fill(20)(ByteString("fghij")))
      val r3 = ApnsPushStreamProvider.send(Seq.fill(20)(ByteString("klmno")))

      Await.result(Future.sequence(Seq(r1, r2, r3)).map(_.flatten), 20 seconds).length must be(60)
    }
  }
}
