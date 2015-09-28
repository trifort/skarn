package skarn.apns

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import akka.io.Tcp
import akka.stream.{OverflowStrategy, ActorMaterializer}
import akka.stream.scaladsl._
import akka.actor.{Props, ActorRef}
import akka.util.ByteString

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

/**
 * Created by yusuke on 15/09/18.
 */

object TlsClientActorProtocol {
  case class SendEncrypted(data: ByteString, promise: Promise[Unit], sender: ActorRef)
  case class ReceiveEncrypted(data: ByteString)
}

object TlsClientActor {
  def props(remoteAddress: InetSocketAddress, timeout: FiniteDuration, sslContext: SSLContext) = Props(new TlsClientActor(remoteAddress, timeout, sslContext))
}

class TlsClientActor(remoteAddress: InetSocketAddress, timeout: FiniteDuration, val sslContext: SSLContext)
  extends TcpClientActor(remoteAddress, timeout) with TlsFlow {
  import TcpClientActorProtocol._
  import TlsClientActorProtocol._
  import Tcp._
  import context.dispatcher

  implicit val m = ActorMaterializer()(context.system)

  val sink1 = Sink.actorRef[SendEncrypted](self, ())
  val sink2 = Sink.actorRef[ReceiveEncrypted](self, ())

  // NOTICE: you MUST slow down upstream
  val source1 = Source.actorRef[Send](100, OverflowStrategy.fail)  // ToDo: use OverflowStrategy.dropNew
  val source2 = Source.actorRef[Received](100, OverflowStrategy.fail) // ToDo: use OverflowStrategy.dropNew

  val runnableTls = FlowGraph.closed(source1, source2)((_, _)) { implicit b => (src1, src2) =>
    import FlowGraph.Implicits._
    val tls = b.add(tlsFlow)
    val snk1 = b.add(sink1)
    val snk2 = b.add(sink2)
    val bcast = b.add(Broadcast[Send](2))
    val extractD = b.add(Flow[Send].map(_.data))
    val extractPR = b.add(Flow[Send].map(s => (s.promise, s.sender)))
    val zip = b.add(Zip[(Promise[Unit], ActorRef), ByteString])
    val merge = b.add(Flow[((Promise[Unit], ActorRef), ByteString)].map{case ((p, ref), data) => SendEncrypted(data, p, ref)})
    val convert = b.add(Flow[ByteString].map(ReceiveEncrypted(_)))
    val payload = b.add(Flow[Received].map(_.data))
    src1 ~> bcast ~> extractD ~> tls.in1
            bcast ~> extractPR ~> zip.in0
                     tls.out1 ~> zip.in1
                                 zip.out ~> merge.inlet
                                            merge.outlet ~> snk1
                       tls.in2 <~ payload <~ src2
    snk2 <~ convert <~ tls.out2
  }


  override def connected(connection: ActorRef): Receive = {
    val (sendRef, receiveRef) = runnableTls.run()
    val r: Receive = {
      case msg: Send => sendRef forward msg
      case msg: Received => receiveRef forward msg
      case SendEncrypted(data, promise, ref) => {
        val cancelTimeout = context.system.scheduler.scheduleOnce(timeout) {
          promise.tryFailure(new Exception("timeout"))
        }
        connection ! Write(data, Ack(promise, cancelTimeout, ref))
      }
      case Ack(promise, timeout, ref) => {
        ref ! Finished
        timeout.cancel()
        promise.success(())
      }
      case ReceiveEncrypted(data) => {
        //publisher ! Receive(data)
      }
      case CommandFailed(Write(data, ack: Ack)) => {
        log.warning("TCP buffer is full. Retry sending.")
        ack.sender ! ReSend(Send(data, ack.promise, ack.sender))
      }
      case _: ConnectionClosed => {
        log.warning("closing connection")
        destroy()
      }
    }
    r
  }
}

object TlsClientRouteeActor {
  def props(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, timeout: FiniteDuration, sslContext: SSLContext) = Props(new TlsClientRouteeActor(id, pool, remoteAddress, timeout, sslContext))
}

object TlsConnectionPoolActor {
  def props(nOfRoutee: Int, remoteAddress: InetSocketAddress, timeout: FiniteDuration, sslContext: SSLContext) = Props(new TlsConnectionPoolActor(nOfRoutee, remoteAddress, timeout, sslContext))
}

class TlsConnectionPoolActor(nOfRoutee: Int, remoteAddress: InetSocketAddress, timeout: FiniteDuration, sslContext: SSLContext) extends TcpConnectionPoolActor(nOfRoutee, remoteAddress, timeout) {
  override def createRoutee(id: Int) = context.actorOf(TlsClientRouteeActor.props(id, pool, remoteAddress, timeout, sslContext))
}

class TlsClientRouteeActor(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, timeout: FiniteDuration, sslContext: SSLContext)
  extends TlsClientActor(remoteAddress, timeout, sslContext) {
  override def destroy() = {
    pool.remove(id)
    super.destroy()
  }
}

trait TlsConnectionPoolRouter extends ConnectionPoolRouter {
  val sslContext: SSLContext
  override def createTcpClientRouter(remoteAddress: InetSocketAddress, timeout: FiniteDuration)
    = TlsConnectionPoolActor.props(maxConnection, remoteAddress, timeout, sslContext)
}

class TlsStreamActor(pool: ConnectionPool, maxRequest: Int) extends TcpStreamActor(pool, maxRequest)

object TlsStreamActor {
  def props(pool: ConnectionPool, maxRequest: Int) = Props(new TlsStreamActor(pool, maxRequest))
}