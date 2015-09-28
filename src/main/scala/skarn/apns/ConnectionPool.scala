package skarn.apns

import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import akka.actor.{ActorContext, ActorRefFactory}

import scala.concurrent.duration.FiniteDuration

/**
 * Created by yusuke on 15/09/25.
 */

sealed trait ConnectionPoolSettings

case class TcpConnectionPoolSettings(remoteAddress: InetSocketAddress, maxRequest: Int, maxConnection: Int, timeout: FiniteDuration) extends ConnectionPoolSettings

case class TlsConnectionPoolSettings(remoteAddress: InetSocketAddress, maxRequest: Int, maxConnection: Int, timeout: FiniteDuration, sslContext: SSLContext)
  extends ConnectionPoolSettings {
  def connectionPoolSettings = TcpConnectionPoolSettings(remoteAddress, maxRequest, maxConnection, timeout)
}

class ConnectionPoolImpl(val settings: TcpConnectionPoolSettings)(implicit factory: ActorRefFactory) extends ConnectionPool with ConnectionPoolRouter {
  import TcpClientActorProtocol._

  val maxConnection = settings.maxConnection

  protected[this] lazy val poolRouterActor = factory.actorOf(createTcpClientRouter(settings.remoteAddress, settings.timeout), s"pool-${settings.remoteAddress.toString.substring(1)}")

  protected[apns] def push(command: Send)(implicit ac: ActorContext) = poolRouterActor.tell(command, ac.self)

  private[apns] def startConnection() = poolRouterActor
}

class TlsConnectionPoolImpl(settings: TlsConnectionPoolSettings)(implicit factory: ActorRefFactory)
  extends ConnectionPoolImpl(settings.connectionPoolSettings) with TlsConnectionPoolRouter {
  val sslContext = settings.sslContext
}

trait ConnectionPool {
  import TcpClientActorProtocol._
  private[apns] def push(command: Send)(implicit ac: ActorContext): Unit
  private[apns] def startConnection(): Unit
}

trait ConnectionPoolCreator[T <: ConnectionPoolSettings] {
  def toPool(settings: T)(implicit factory: ActorRefFactory): ConnectionPool
}

object ConnectionPool {
  object Implicits {
    implicit object TcpConnectionPoolCreator extends ConnectionPoolCreator[TcpConnectionPoolSettings] {
      def toPool(settings: TcpConnectionPoolSettings)(implicit factory: ActorRefFactory) = new ConnectionPoolImpl(settings)(factory)
    }
    implicit object TlsConnectionPoolCreator extends ConnectionPoolCreator[TlsConnectionPoolSettings] {
      def toPool(settings: TlsConnectionPoolSettings)(implicit factory: ActorRefFactory) = new TlsConnectionPoolImpl(settings)(factory)
    }
  }

  def create[T <: ConnectionPoolSettings](settings: T)(implicit creator: ConnectionPoolCreator[T], factory: ActorRefFactory) = creator.toPool(settings)(factory)
}