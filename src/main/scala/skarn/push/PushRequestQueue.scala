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
  case class Concat(messages: Array[QueueRequest])
  case object StartStream
  case class Done(id: Long, start: Option[Long] = None) extends Command
  case class Retry(id: Long) extends Command
  sealed trait Reply
  case object Accepted extends Reply
  case object Denied extends Reply
  case class GetBuffer(n: Int = -1)
  case class CurrentBuffer(buffer: Vector[QueueRequest])
  case class GetProcessing(n: Int = -1)
  case class CurrentProcessing(buffer: Map[Long, QueueRequest])
  case class QueueRequest(id: Long, entity: PushEntity, start: Option[Long] = None, retry: Short = 0)
  def props(maxRetry: Short, pushActorRef: ActorRef, maxQueueSize: Int = 1000): Props = {
    Props(new PushRequestQueue(maxRetry, pushActorRef, maxQueueSize))
      .withDispatcher("push-request-queue-dispatcher")
  }
}

class PushRequestQueue(maxRetry: Short, pushActorRef: ActorRef, val maxQueueSize: Int) extends ActorSubscriber with ActorPublisher[PushRequestQueue.QueueRequest] with ActorLogging with PushFlow {
  import akka.stream.actor.ActorPublisherMessage._
  import akka.stream.actor.ActorSubscriberMessage._
  import PushRequestQueue._

  val ref = pushActorRef

  var buf = Buffer.empty

  private[this] lazy val __source = {
    implicit val materializer = ActorMaterializer()
    Source(ActorPublisher(self)).via(pushFlow).runWith(Sink(ActorSubscriber[Command](self)))
  }

  override val requestStrategy = new MaxInFlightRequestStrategy(max = maxQueueSize) {
    override def inFlightInternally: Int = buf.buffer.size + buf.processing.size
  }

  def receive: Receive = {
    case StartStream => {
      __source
    }
    case Append(message) if (buf.total == maxQueueSize ) => {
      sender() ! Denied
    }
    case Append(message) => {
      sender() ! Accepted
      if (buf.buffer.isEmpty && totalDemand > 0) {
        buf = buf.immediatelyProcess(message)
        onNext(message)
      } else {
        buf = buf.append(message)
        deliverBuf()
      }
      log.debug("[buffer size] {}/{}", buf.buffer.length, maxQueueSize)
    }
    case Concat(messages) if ((buf.total + messages.length) >= maxQueueSize ) =>  {
      sender() ! Denied
    }
    case Concat(messages) => {
      sender() ! Accepted
      messages.foreach { message =>
        if (buf.buffer.isEmpty && totalDemand > 0) {
          buf = buf.immediatelyProcess(message)
          onNext(message)
        } else {
          buf = buf.append(message)
          deliverBuf()
        }
      }
      log.debug("[buffer size] {}/{}", buf.buffer.length, maxQueueSize)
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
      buf = buf.doneWith(id)
      log.debug("[buffer size] {}/{}", buf.buffer.length, maxQueueSize)
    }
    case Retry(id) => {
      buf.processing.get(id) match {
        case Some(message) => {
          if (message.retry < maxRetry) {
            buf = buf.retry(id)
            deliverBuf()
          } else {
            log.warning("[id:{}] max retry count exceeded", id)
            self ! Done(id)
          }
        }
        case None => log.warning("[id:{}] missing message to retry", id)
      }
    }
    case GetBuffer(n) => {
      val buffer = if (n < 0) buf.buffer else buf.buffer.take(n)
      sender() ! CurrentBuffer(buffer)
    }
    case GetProcessing(n) => {
      val p = if (n < 0) buf.processing else buf.processing.take(n)
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
        val (after, use) = buf.process(totalDemand.toInt)
        buf = after
        use foreach onNext
      } else {
        val (after, use) = buf.process(Int.MaxValue)
        buf = after
        use foreach onNext
        deliverBuf()
      }
    }
  }
}
