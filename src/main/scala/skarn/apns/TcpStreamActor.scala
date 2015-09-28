package skarn.apns

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import akka.actor._
import akka.io.{IO, Tcp}
import akka.stream.actor._
import akka.util.ByteString
import scala.concurrent.duration.{FiniteDuration}
import scala.concurrent.Promise

/**
 * Created by yusuke on 15/09/14.
 */

object TcpClientActorProtocol {
  case object StartConnection
  case class Send(data: ByteString, promise: Promise[Unit], sender: ActorRef)
  case class Receive(data: ByteString)
  case class ReSend(command: Send)
  case object Finished
}

private case class Ack(promise: Promise[Unit], timeout: Cancellable, sender: ActorRef) extends Tcp.Event

object TcpClientActor {
  def props(remoteAddress: InetSocketAddress, timeout: FiniteDuration) = Props(new TcpClientActor(remoteAddress, timeout))
}

class TcpClientActor(remoteAddress: InetSocketAddress, timeout: FiniteDuration) extends Actor with ActorLogging {
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
      send.sender ! ReSend(send)
    }
  }

  def connected(connection: ActorRef): Receive = {
    case Send(data, promise, ref) => {
      val cancelTimeout = system.scheduler.scheduleOnce(timeout) {
        promise.tryFailure(new Exception("timeout"))
      }
      connection ! Write(data, Ack(promise, cancelTimeout, ref))
    }
    case Ack(promise, timeout, ref) => {
      ref ! Finished
      timeout.cancel()
      promise.success(())
    }
    case Received(data) => //publisher ! Receive(data)
    case CommandFailed(Write(data, ack: Ack)) => {
      log.warning("TCP buffer is full. Retry sending.")
      ack.sender ! ReSend(Send(data, ack.promise, ack.sender))
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
  def props(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, timeout: FiniteDuration) = Props(new TcpClientRouteeActor(id: Int, pool, remoteAddress, timeout))
}

class TcpClientRouteeActor(id: Int, pool: ConcurrentHashMap[Int, ActorRef], remoteAddress: InetSocketAddress, timeout: FiniteDuration) extends TcpClientActor(remoteAddress, timeout) {
  override def destroy() = {
    pool.remove(id)
    super.destroy()
  }
}

object TcpConnectionPoolActor {
  def props(nOfRoutee: Int, remoteAddress: InetSocketAddress, timeout: FiniteDuration) = Props(new TcpConnectionPoolActor(nOfRoutee, remoteAddress, timeout))
}

class TcpConnectionPoolActor(nOfRoutee: Int, remoteAddress: InetSocketAddress, timeout: FiniteDuration) extends Actor {

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

  def createRoutee(id: Int) = context.actorOf(TcpClientRouteeActor.props(id, pool, remoteAddress, timeout))


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
  protected[this] def createTcpClientRouter(remoteAddress: InetSocketAddress, timeout: FiniteDuration) =
    TcpConnectionPoolActor.props(maxConnection, remoteAddress, timeout)
}

object DeadTcpWriteWatcher {
  def props(reportTo: ActorRef) = Props(new DeadTcpWriteWatcher(reportTo))
}

class DeadTcpWriteWatcher(reportTo: ActorRef) extends Actor {
  import Tcp._
  import TcpClientActorProtocol._
  context.system.eventStream.subscribe(self, classOf[DeadLetter])
  def receive: Receive = {
    case DeadLetter(Write(data, Ack(promise, _, ref)), _, _) if (!promise.isCompleted) => {
      reportTo ! ReSend(Send(data, promise, ref))
    }
  }

  override def postStop() = {
    context.system.eventStream.unsubscribe(self, classOf[DeadLetter])
  }
}

object TcpStreamActorProtocol {
  case class Push(data: ByteString, promise: Promise[Unit])
}

object TcpStreamActor {
  def props(pool: ConnectionPool, maxRequest: Int) = Props(new TcpStreamActor(pool, maxRequest))
}

class TcpStreamActor(pool: ConnectionPool, maxRequest: Int) extends ActorPublisher[ByteString]
with ActorSubscriber {
  import TcpClientActorProtocol._
  import TcpStreamActorProtocol._
  import ActorSubscriberMessage._

  var nOfHandlingRequest = 0
  var isUpStreamCompleted: Boolean = false

  override def requestStrategy = new MaxInFlightRequestStrategy(maxRequest) {
    override def inFlightInternally = nOfHandlingRequest
  }

  context.actorOf(DeadTcpWriteWatcher.props(self), "deadletter-watcher")

  def receive: Receive = {
    case OnNext(Push(data, promise)) => {
      pool.push(Send(data, promise, self))
      nOfHandlingRequest += 1
    }
    case OnComplete => {
      isUpStreamCompleted = true
      if (nOfHandlingRequest == 0) context.stop(self)
    }
    case ReSend(command) => {
      pool.push(command)
    }
    case Finished => {
      nOfHandlingRequest -= 1
      if (nOfHandlingRequest == 0 && isUpStreamCompleted) context.stop(self)
    }
    case Receive(data) if totalDemand > 0 => {
      onNext(data)
    }
  }
}