package skarn.push

import akka.actor.{ActorRef, ActorLogging, Props}
import akka.stream.ActorMaterializer
import akka.stream.actor.{MaxInFlightRequestStrategy, ActorSubscriber, ActorPublisher}
import akka.stream.scaladsl.{Source, Sink}
import skarn.push.PushRequestHandleActorProtocol.PushEntity


/**
 * Created by yusuke on 15/08/12.
 */

object PushRequestQueue {
  sealed trait Command
  case class Append(message: QueueRequest)
  case object StartStream
  case class Done(id: Int, start: Option[Long] = None) extends Command
  case class Retry(id: Int) extends Command
  case class GetBuffer(n: Int = -1)
  case class CurrentBuffer(buffer: Vector[QueueRequest])
  case class GetProcessing(n: Int = -1)
  case class CurrentProcessing(buffer: Map[Int, QueueRequest])
  case class QueueRequest(id: Int, entity: PushEntity, start: Option[Long] = None, retry: Short = 0)
  def props(maxRetry: Short, pushActorRef: ActorRef): Props = Props(new PushRequestQueue(maxRetry, pushActorRef))
}

class PushRequestQueue(maxRetry: Short, pushActorRef: ActorRef) extends ActorSubscriber with ActorPublisher[PushRequestQueue.QueueRequest] with ActorLogging with PushFlow {
  import akka.stream.actor.ActorPublisherMessage._
  import akka.stream.actor.ActorSubscriberMessage._
  import PushRequestQueue._

  val ref = pushActorRef

  val maxQueueSize = 100000
  var buf = Vector.empty[QueueRequest]
  var processing = Map.empty[Int, QueueRequest]

  private[this] lazy val __source = {
    implicit val materializer = ActorMaterializer()
    Source(ActorPublisher(self)).via(pushFlow).runWith(Sink(ActorSubscriber[Command](self)))
  }

  override val requestStrategy = new MaxInFlightRequestStrategy(max = maxQueueSize) {
    override def inFlightInternally: Int = buf.size + processing.size
  }

  def receive: Receive = {
    case StartStream => {
      __source
    }
    case Append(message) => {
      if (buf.isEmpty && totalDemand > 0) {
        onNext(message)
      } else {
        buf = buf :+ message
        deliverBuf()
      }
    }
    case Done(id, start) => {
      start match {
        case Some(timestamp) => {
          log.info("[id:{}] push is completed in {}ns", id, System.nanoTime() - timestamp)
        }
        case None => {
          log.info("[id:{}] push is completed", id)
        }
      }
      processing = processing - id
    }
    case Retry(id) => {
      processing.get(id) match {
        case Some(message) => {
          if (message.retry < maxRetry) {
            log.info("[id:{}] start retrying...", id)
            buf = buf :+ message.copy(retry = (message.retry + 1).toShort)
            deliverBuf()
          } else {
            log.warning("[id:{}] max retry count exceeded", id)
          }
          processing = processing - id
        }
        case None => log.warning("[id:{}] missing message to retry", id)
      }
    }
    case GetBuffer(n) => {
      val buffer = if (n < 0) buf else buf.take(n)
      sender() ! CurrentBuffer(buffer)
    }
    case GetProcessing(n) => {
      val p = if (n < 0) processing else processing.take(n)
      sender() ! CurrentProcessing(p)
    }
    case OnNext(m: Command) => {
      self forward m
    }
    case Request(n) => {
      deliverBuf()
    }
    case Cancel => {
      log.warning("canceled")
    }
  }

  @annotation.tailrec
  final def deliverBuf(): Unit = {
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        processing = processing ++ use.map(m => (m.id, m))
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        processing = processing ++ use.map(m => (m.id, m))
        use foreach onNext
        deliverBuf()
      }
    }
  }
}
