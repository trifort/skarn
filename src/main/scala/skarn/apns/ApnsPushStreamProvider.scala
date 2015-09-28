package skarn.apns

import akka.stream.{FanOutShape2, Materializer}
import akka.stream.scaladsl._
import akka.util.ByteString
import skarn.push.ApnsService
import collection.immutable.Seq
import scala.concurrent.{Future}

/**
 * Created by yusuke on 15/09/24.
 */

trait ApnsPushStreamProvider extends TcpClientStream {
  type Response = Future[Unit]

  import TcpStreamActorProtocol._

  val service: ApnsService

  val ackResponseSink = Sink.fold[Seq[Response], Response](Seq.empty)(_ :+ _)
  val serverResponseSink = Sink.foreach[ByteString](response => println(s"APNS respond with $response, which indicate some failure"))

  lazy val combinedSink = Sink(ackResponseSink) { implicit b => ack =>
    import FlowGraph.Implicits._
    val p = b.add(pipeline)
    val response = b.add(serverResponseSink)
    p.out0 ~> ack
    p.out1 ~> response.inlet
    p.in
  }

  def send(data: Seq[ByteString])(implicit m: Materializer) = {
    import m.executionContext
    Source(data).runWith(combinedSink).flatMap(Future.sequence(_))
  }

  /*
   * IN[ByteString] ~> convert ~> request | response ~> OUT[ByteString]
   *                           ~> future ~> OUT[Future[Unit]]
   */
  lazy val pipeline = FlowGraph.partial() { implicit b =>
    import FlowGraph.Implicits._
    val bcast = b.add(Broadcast[Push](2))
    val convert = b.add(convertFlow)
    val request = b.add(requestSink)
    val future = b.add(requestFuture)
    val response = b.add(responseSource)

    convert ~> bcast ~> request
               bcast ~> future
    new FanOutShape2(convert.inlet, future.outlet, response.outlet)
  }
}
