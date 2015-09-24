package skarn.apns

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConversions._
import akka.actor._
import akka.io.{IO, Tcp}
import akka.routing.{BalancingPool, DefaultResizer, SmallestMailboxPool}
import akka.stream.actor._
import akka.util.ByteString
import scala.concurrent.duration.{FiniteDuration}
import scala.concurrent.Promise

/**
 * Created by yusuke on 15/09/14.
 */

object TcpClientActorProtocol {
  case object StartConnection
  case class Send(data: ByteString, promise: Promise[Unit])
  case class Receive(data: ByteString)
  case class ReSend(command: Send)
  case object Finished
}

private case class Ack(promise: Promise[Unit], timeout: Cancellable) extends Tcp.Event

object TcpClientActor {
  def props(remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) = Props(new TcpClientActor(remoteAddress, publisher, timeout))
}

class TcpClientActor(remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) extends Actor with ActorLogging {
  import TcpClientActorProtocol._
  import Tcp._
  import context.system
  import context.dispatcher

  def receive = waiting

  def waiting: Receive = {
    case StartConnection => {
      IO(Tcp) ! Connect(remoteAddress)
    }
    case Connected(remote, local) => {
      val connection = sender()
      connection ! Register(self, keepOpenOnPeerClosed = true)
      context.become(connected(connection))
    }
    case send: Send => {
      log.warning("connection is not established yet so retry.")
      publisher ! ReSend(send)
    }
  }

  def connected(connection: ActorRef): Receive = {
    case Send(data, promise) => {
      val cancelTimeout = system.scheduler.scheduleOnce(timeout) {
        promise.tryFailure(new Exception("timeout"))
      }
      connection ! Write(data, Ack(promise, cancelTimeout))
    }
    case Ack(promise, timeout) => {
      publisher ! Finished
      timeout.cancel()
      promise.success(())
    }
    case Received(data) => publisher ! Receive(data)
    case CommandFailed(Write(data, ack: Ack)) => {
      log.warning("TCP buffer is full. Retry sending.")
      publisher ! ReSend(Send(data, ack.promise))
    }
    case _: ConnectionClosed => destroy()
  }

  def destroy() = {
    context.stop(self)
  }

  override def preStart() = {
    super.preStart()
    self ! StartConnection
    log.info("starting TCP connection")
  }
}

object TcpClientRouteeActor {
  def props(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) = Props(new TcpClientRouteeActor(id: Int, pool, remoteAddress, publisher, timeout))
}

class TcpClientRouteeActor(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) extends TcpClientActor(remoteAddress, publisher, timeout) {
  override def destroy() = {
    pool.remove(id)
    super.destroy()
  }
}

object TcpConnectionPool {
  def props(nOfRoutee: Int, remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) = Props(new TcpConnectionPool(nOfRoutee, remoteAddress, publisher, timeout))
}

class TcpConnectionPool(nOfRoutee: Int, remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) extends Actor {

  val pool = new ConcurrentHashMap[Int, ActorRef]()

  object Index {
    private[this] var index = 0
    final def current = index
    final def update() = index = (index + 1) % nOfRoutee
  }

  def receive: Receive = {
    case msg => {
      val current = pool.getOrElseUpdate(Index.current, createRoutee(Index.current))
      current forward msg
      Index.update()
    }
  }

  def createRoutee(id: Int) = context.actorOf(TcpClientRouteeActor.props(id, pool, remoteAddress, publisher, timeout))


  override def preStart() = {
    super.preStart()
    val routees = 0 until nOfRoutee map { n =>
      n -> createRoutee(n)
    }
    pool.putAll(routees.toMap[Int, ActorRef])
  }
}

trait ConnectionPoolRouter {
  val maxConnection: Int
  def createTcpClientRouter(remoteAddress: InetSocketAddress, publisher: ActorRef, timeout: FiniteDuration) =
    TcpConnectionPool.props(maxConnection, remoteAddress, publisher, timeout)
}

object DeadTcpWriteWatcher {
  def props(reportTo: ActorRef) = Props(new DeadTcpWriteWatcher(reportTo))
}

class DeadTcpWriteWatcher(reportTo: ActorRef) extends Actor {
  import Tcp._
  import TcpClientActorProtocol._
  context.system.eventStream.subscribe(self, classOf[DeadLetter])
  def receive: Receive = {
    case DeadLetter(Write(data, Ack(promise, _)), _, _) if (!promise.isCompleted) => {
      reportTo ! ReSend(Send(data, promise))
    }
  }

  override def postStop() = {
    context.system.eventStream.unsubscribe(self, classOf[DeadLetter])
  }
}

object TcpStreamActor {
  def props(remoteAddress: InetSocketAddress, maxRequest: Int, maxConnection: Int, timeout: FiniteDuration) = Props(new TcpStreamActor(remoteAddress, maxRequest, maxConnection, timeout))
}

class TcpStreamActor(remoteAddress: InetSocketAddress, maxRequest: Int, val maxConnection: Int, timeout: FiniteDuration) extends ActorPublisher[ByteString]
with ActorSubscriber with ConnectionPoolRouter {
  import TcpClientActorProtocol._
  import ActorSubscriberMessage._

  var nOfHandlingRequest = 0

  override def requestStrategy = new MaxInFlightRequestStrategy(maxRequest) {
    override def inFlightInternally = nOfHandlingRequest
  }

  val tcpClient = context.actorOf(createTcpClientRouter(remoteAddress, self, timeout), "tcp-client-router")
  context.actorOf(DeadTcpWriteWatcher.props(self), "deadletter-watcher")

  def receive: Receive = {
    case OnNext(send: Send) => {
      tcpClient ! send
      nOfHandlingRequest += 1
    }
    case OnComplete =>
    case ReSend(command) => {
      tcpClient ! command
    }
    case Finished => nOfHandlingRequest -= 1
    case Receive(data) if totalDemand > 0 => {
      onNext(data)
    }
  }
}