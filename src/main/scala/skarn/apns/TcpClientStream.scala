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

  val target: ActorRef

  protected[this] val convertFlow = Flow[ByteString].map(d => Send(d, Promise[Unit]))

  protected[this] lazy val requestSink = Sink(ActorSubscriber[Send](target))

  protected[this] lazy val responseSource = Source(ActorPublisher[ByteString](target))

  protected [this] val requestFuture = Flow[Send].map(_.promise.future)

}

