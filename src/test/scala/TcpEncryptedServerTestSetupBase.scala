package skarn

import javax.net.ssl.SSLContext
import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.io.Inet.SocketOption
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.{BidiShape, Materializer}
import akka.stream.io._
import akka.stream.scaladsl._
import akka.stream.testkit.{TestSubscriber, TestPublisher}
import akka.util.ByteString
import scala.concurrent.Future
import scala.collection.immutable.Seq
import scala.util.control.NonFatal
import scala.concurrent.duration._

/**
 * Created by yusuke on 15/09/07.
 */

// reference: https://github.com/akka/akka/blob/releasing-akka-stream-and-http-experimental-1.0/akka-http-core/src/test/scala/akka/http/impl/engine/server/HttpServerTestSetupBase.scala


trait TcpEncryptedServerTestSetupBase {
  implicit def system: ActorSystem
  implicit def materializer: Materializer
  val sslContext: SSLContext
  val testFlow: Flow[ByteString, ByteString, Unit]
  val probe: ActorRef

  private[this] lazy val tlsBidiFlow = SslTls(sslContext, NegotiateNewSession(None, None, None, None), Role.server)

  private[this] val parser = BidiFlow() { implicit b =>
    val wrapTls = b.add(Flow[ByteString].map(b => {println("server: wrapTls", b); SendBytes(b)}))
    val unwrapTls = b.add(Flow[SslTlsInbound].collect {
      case SessionBytes(session, bytes) => {println("server: unwrapTls", session, bytes.toString); bytes}
    })

    BidiShape(wrapTls, unwrapTls)
  }

  lazy val (netIn, netOut, probeFlow) = {
    val netIn = TestPublisher.manualProbe[ByteString]()
    val netOut = TestSubscriber.manualProbe[ByteString]

    val flow = BidiFlow(parser atop tlsBidiFlow) { implicit b =>
      tls =>
        import FlowGraph.Implicits._
        val test = b.add(testFlow)
        val bcast = b.add(Broadcast[ByteString](2))
        val merge = b.add(Merge[ByteString](2))
        Source(netIn) ~> merge ~> tls.in1
        tls.out2 ~> test ~> bcast ~> Sink(netOut)
        BidiShape(merge.in(1), tls.out1, tls.in2, bcast.out(1))
    }
    (netIn, netOut, flow)
  }

  def bind(interface: String, port: Int,
           backlog: Int = 100,
           options: collection.immutable.Traversable[SocketOption] = Nil,
           halfClose: Boolean = false,
           idleTimeout: Duration = Duration.Inf)(implicit fm: Materializer): Source[IncomingConnection, Future[ServerBinding]] = {

    val connections = Tcp().bind(interface, port, backlog, options, halfClose = false, idleTimeout)

    connections.map {
      case Tcp.IncomingConnection(localAddress, remoteAddress, flow) => {
        println(localAddress, remoteAddress)
        IncomingConnection(localAddress, remoteAddress, probeFlow join flow)
      }
    }.mapMaterializedValue {
      _.map(tcpBinding => ServerBinding(tcpBinding.localAddress)(() => tcpBinding.unbind()))(fm.executionContext)
    }
  }


  def bindAndHandle(handler: Flow[ByteString, ByteString, _], interface: String, port: Int,
    backlog: Int = 100, options: collection.immutable.Traversable[SocketOption] = Nil, halfClose: Boolean = false,
    idleTimeout: Duration = Duration.Inf)(implicit m: Materializer): Future[ServerBinding] = {
    bind(interface, port, backlog, options, halfClose, idleTimeout)(m).to(Sink.foreach { conn: IncomingConnection =>
      probe ! "connected"
      conn.flow.join(handler).run()(m)
    }).run()(m)
  }

  lazy val inSub = netIn.expectSubscription()
  lazy val outSub = netOut.expectSubscription()

}
