package skarn

import java.net.InetSocketAddress
import akka.actor._
import akka.io.{ IO, Tcp }
import akka.util.ByteString

/**
 * Created by yusuke on 15/09/14.
 */
class TcpServerTestSetupBase(system: ActorSystem, target: ActorRef) {
  val serverActor = system.actorOf(TcpServer.props(target))
}

object TcpServer {
  def props(probe: ActorRef) = Props(new TcpServer(probe))
}

class TcpServer(probe: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0))

  def receive = waiting

  def waiting: Receive = {
    case b @ Bound(localAddress) => {
      log.info("listening on port {}", localAddress.getPort)
      probe ! b
    }

    case CommandFailed(_: Bind) => context stop self

    case c@Connected(remote, local) => {
      log.info("connection established with {}", remote)
      probe ! c
      val connection = sender()
      val handler = context.actorOf(TcpServerHandler.props(connection, probe))
      probe ! Register(handler)
      connection ! Register(handler)
    }
  }
}

object TcpServerHandler {
  case class TestSend(data: ByteString)
  def props(connection: ActorRef, probe: ActorRef) = Props(new TcpServerHandler(connection, probe))
}

class TcpServerHandler(connection: ActorRef, probe: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import TcpServerHandler._

  def receive: Receive = {
    case r: Received => probe ! r
    case p@PeerClosed => {
      log.info("connection closed")
      probe ! p
      context stop self
    }
    case c: CloseCommand => {
      connection ! c
      log.info("closing connection")
    }
    case TestSend(data) => connection ! Write(data)
    case Closed => log.info("connection closed")
    case other => log.warning("unknown message {}", other)
  }
}