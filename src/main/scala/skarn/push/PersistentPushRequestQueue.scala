package skarn.push

import akka.actor.SupervisorStrategy.{Escalate}
import akka.actor._
import akka.persistence.RecoveryCompleted
import skarn.push.PushRequestQueue.QueueRequest
import scala.concurrent.duration._

import scala.util.control.NonFatal

/**
 * Created by yusuke on 15/09/02.
 */

object PersistentBufferJournalProtocol {
  case class RecoveryComplete(data: Map[Int, QueueRequest])
}

class PersistentBufferJournal(persistenceId: String, target: ActorRef) extends PersistentJournalActor(persistenceId, target) {
  import PushRequestQueue._
  import PersistentPushRequestQueueProtocol._
  import PersistentBufferJournalProtocol._

  var recoveringTemporaryData: Map[Int, QueueRequest] = Map.empty

  def updateState(p: Persistent) = {
    p match {
      case AppendEvt(message) => target forward Append(message)
      case ConcatEvt(messages) => target forward Concat(messages)
      case DoneEvt(id, start) =>
    }
  }

  override def receiveRecover: Receive = {
    case p: Persistent => p match {
      case AppendEvt(message) => {
        recoveringTemporaryData = recoveringTemporaryData + (message.id -> message)
      }
      case ConcatEvt(messages) => {
        recoveringTemporaryData = recoveringTemporaryData ++ messages.map(m => (m.id, m))
      }
      case DoneEvt(id, start) => {
        recoveringTemporaryData = recoveringTemporaryData - id
      }
    }
    case RecoveryCompleted => {
      target ! RecoveryComplete(recoveringTemporaryData)
      recoveringTemporaryData = Map.empty
    }
  }
}

object PersistentBufferJournal {
  def props(persistenceId: String, target: ActorRef) = Props(new PersistentBufferJournal(persistenceId, target))
}

object PersistentPushRequestQueueProtocol {
  case class AppendEvt(message: QueueRequest) extends Persistent
  case class ConcatEvt(messages: Array[QueueRequest]) extends Persistent
  case class DoneEvt(id: Int, start: Option[Long] = None) extends Persistent
  case object CurrentState
  case object Recovering
  case object Recovered
}

object PersistentPushRequestQueue {
  def props(maxRetry: Short, pushActorRef: ActorRef, maxQueueSize: Int, persistenceId: String) = Props(new PersistentPushRequestQueue(maxRetry, pushActorRef, maxQueueSize, persistenceId))
}

class PersistentPushRequestQueue(maxRetry: Short, pushActorRef: ActorRef, maxQueueSize: Int, persistenceId: String) extends PushRequestQueue(maxRetry, pushActorRef, maxQueueSize) {
  import PushRequestQueue._
  import PersistentBufferJournalProtocol._
  import PersistentPushRequestQueueProtocol._
  val persistentBuffer = context.actorOf(PersistentBufferJournal.props(persistenceId, self))

  override def supervisorStrategy = AllForOneStrategy() {
    case NonFatal(e) => Escalate
  }

  def recovering: Receive = {
    case RecoveryComplete(temp) => {
      buf = buf.copy(buffer = temp.map(_._2).toVector)
      log.info("recovery completed")
      context.become(persisting)
    }
    case CurrentState => sender ! Recovering
  }

  def persisting: Receive = {
    val receive: Receive = {
      case evt: Persistent => {
        persistentBuffer forward evt
      }
      case done@Done(id, start) => {
        super.receive(done)
        persistentBuffer ! DoneEvt(id, start)
      }
      case CurrentState => sender ! Recovered
    }
    receive orElse super.receive
  }

  override def receive: Receive = recovering
}
