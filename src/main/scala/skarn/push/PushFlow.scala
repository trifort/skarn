package skarn.push

import akka.actor.ActorRef
import akka.stream.scaladsl.{Flow}
import skarn.push.PushRequestQueue.{Command, QueueRequest}

import scala.concurrent.Promise

/**
 * Created by yusuke on 15/08/18.
 */

object PushFlow {
  case class PushEntityWrap(request: QueueRequest, promise: Promise[Command])
}

trait PushFlow {
  import PushFlow._

  val ref: ActorRef

  def push(request: QueueRequest) = {
    val p = Promise[Command]
    ref ! PushEntityWrap(request, p)
    p.future
  }

  lazy val pushFlow = {
    Flow[QueueRequest].mapAsyncUnordered(8)(push)
  }
}
