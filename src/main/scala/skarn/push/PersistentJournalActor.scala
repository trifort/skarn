package skarn.push

import akka.actor.{ActorRef, ActorLogging}
import akka.persistence.{PersistentActor}
import akka.persistence._

/**
 * Created by yusuke on 15/08/31.
 */

trait Persistent


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
