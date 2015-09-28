package skarn.apns

import akka.actor.ActorRef
import akka.stream.Materializer
import akka.stream.actor.{ActorSubscriber, ActorPublisher}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.{Future, Promise}

/**
 * Created by yusuke on 15/09/14.
 */

trait TcpClientStream {
  import TcpClientActorProtocol._

  val connectionPool: ConnectionPool
  val maxRequest: Int

  protected[this] val convertFlow = Flow[ByteString].map(Send(_, Promise[Unit]))

  protected[this] def requestSink = Sink.actorSubscriber[Send](TlsStreamActor.props(connectionPool, maxRequest))

  protected[this] def responseSource = Source.actorPublisher[ByteString](TlsStreamActor.props(connectionPool, maxRequest))

  protected [this] val requestFuture = Flow[Send].map(_.promise.future)

}

