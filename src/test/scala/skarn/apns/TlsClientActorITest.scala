package skarn.apns

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.testkit.{TestProbe, TestKit}
import akka.util.ByteString
import kamon.Kamon
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.apns.TcpClientActorProtocol.Send
import skarn.push.{Apns, ApnsService}
import skarn.{TcpEncryptedServerTestSetupBase, StopSystemAfterAllWithAwaitTermination}
import scala.concurrent.duration._
import scala.concurrent.{Promise, Await}

/**
* Created by yusuke on 15/09/24.
*/
class TlsClientActorITest extends TestKit({Kamon.start(); ActorSystem("TlsClientActorITest")})
with WordSpecLike with MustMatchers with StopSystemAfterAllWithAwaitTermination { testSelf =>
  "TlsClientActor" must {

    val apnsService = new ApnsService {
      val certificate = Apns.loadCertificateFromClassPath("server_keystore").get
      val password = "passwd"
    }

    "send a request" in {
      new TcpEncryptedServerTestSetupBase {
        implicit val system = testSelf.system
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        val sslContext = apnsService.sslContext
        val testFlow = Flow[ByteString].map(v => {println("** server received a request **"); v})

        val tcpserver = bindAndHandle(testFlow, "localhost", 0)
        val serverBinding = Await.result(tcpserver, 5 seconds)

        val publisher = TestProbe()
        val testProbe = TestProbe()
        val probe = testProbe.ref

        val client = system.actorOf(TlsClientActor.props(serverBinding.localAddress, 5 seconds, sslContext))

        testProbe.expectMsg("connected")

        // To ensure to flush TCP buffer. Ugly test.
        Range(0, 10) foreach { _ =>
          val p1 = Promise[Unit]
          client ! Send(ByteString("abcde"), p1)
        }

        outSub.request(1)
        netOut.expectNext() must be(ByteString("abcde"))

        Await.result(serverBinding.unbind(), 5 seconds)
      }
    }
  }
}
