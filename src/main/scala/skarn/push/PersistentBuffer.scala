package skarn.push

import akka.actor.{ActorRef, ActorLogging, Props}
import akka.persistence.{PersistentActor, Update, PersistentView}
import akka.stream.actor.ActorPublisher
import akka.persistence._
import scala.concurrent.duration.Duration

/**
 * Created by yusuke on 15/08/31.
 */

//reference: https://github.com/akka/akka/blob/master/akka-stream/src/main/scala/akka/persistence/stream/PersistentPublisher.scala

object PersistentBuffer {
  case class Response(buffer: Vector[Persistent])
  case class Request(num: Int)
  case object Fill
  def props(publisher: ActorRef, persistenceId: String, viewId: String, maxBufferSize: Int) = Props(new PersistentBuffer(publisher, persistenceId, viewId, maxBufferSize))
}

trait Persistent

class PersistentBuffer(publisher: ActorRef, val persistenceId: String, val viewId: String, val maxBufferSize: Int) extends PersistentView with ActorLogging {
  import PersistentBuffer._

  private var requested = 0
  private var replayed = 0
  private var buffer: Vector[Persistent] = Vector.empty

  def receive: Receive = {
    case p: Persistent => {
      log.info("Persistent: {}", p)
      buffer = buffer :+ p
      replayed += 1
      if (requested > 0) respond(requested)
    }
    case Request(num) => {
      log.info("Request: {}", num)
      requested += num
      if (buffer.nonEmpty) respond(requested)
      fill()
    }
    case Fill => fill()
    case msg => log.info("other: {}", msg)
  }

  override final def autoUpdate = false

  override def autoUpdateInterval = Duration.Zero

  override final def autoUpdateReplayMax = {
    log.info("autoUpdateReplayMax: {}", maxBufferSize - buffer.size)
    maxBufferSize - buffer.size
  }

  def respond(num: Int) = {
    val (res, buf) = buffer.splitAt(num)
    publisher ! Response(res)
    buffer = buf
    requested -= res.size
    log.info("respond: {}", res)
  }

  def fill() = {
    val diff = autoUpdateReplayMax - replayed + requested
    if (diff > 0) {
      log.info("filling: {}", diff)
      self ! Update(false, diff)
      replayed = 0
    }
  }
}

trait UpdateState {
  def updateState(p: Persistent): Unit
}

abstract class PersistentJournalActor(val persistenceId: String, target: ActorRef) extends PersistentActor with UpdateState with ActorLogging {
  import PersistentJournalProtocol._
  protected var count = 0

  def receiveCommand = {
    case msg: Persistent => {
      persist(msg) { evt =>
        count += 1
        updateState(evt)
        log.info("journal: {}", count)
      }
    }
  }

  def receiveRecover: Receive = {
    case _: Persistent => count += 1
    case RecoveryCompleted => target ! RecoveryComplete(count)
  }
}

object PersistentJournalProtocol {
  case class RecoveryComplete(count: Int)
}

object PersistentPublisher {
  case class Persist(payload: Persistent)
  def props(persistenceId: String, persistentJournalCreator: (String, ActorRef) => Props) = Props(new PersistentPublisher(persistenceId, persistentJournalCreator))
}

class PersistentPublisher(persistenceId: String, persistentJournalCreator: (String, ActorRef) => Props) extends ActorPublisher[Persistent] with ActorLogging {
  import akka.stream.actor.ActorPublisherMessage._
  import PersistentPublisher._

  val journal = context.actorOf(persistentJournalCreator(persistenceId, self), "journal")
  val buffer = context.actorOf(PersistentBuffer.props(self, persistenceId, s"$persistenceId-view", 100), "buffer")

  def recovering: Receive = {
    case PersistentJournalProtocol.RecoveryComplete(count) => {
      log.info("recovery completed {}", count)
      context.become(waiting)
      buffer ! PersistentBuffer.Fill
    }
  }

  def waiting: Receive = {
    case Persist(msg) => {
      log.info("Persist({})", msg)
      journal ! msg
      deliverBuf()
    }
    case PersistentBuffer.Response(buf) => {
      log.info("buffer.response: {}", buf)
      buf.foreach(onNext)
    }
    case Request(_) => deliverBuf()
    case Cancel => context.stop(self)
    case other => log.warning("other: {}", other)
  }

  def receive = recovering

  @annotation.tailrec
  final def deliverBuf(): Unit =
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        buffer ! PersistentBuffer.Request(totalDemand.toInt)
      } else {
        buffer ! PersistentBuffer.Request(Int.MaxValue)
        deliverBuf()
      }
    }
}