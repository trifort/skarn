package skarn.push

import akka.actor.{Props, ActorRef}

/**
 * Created by yusuke on 15/09/04.
 */
class PersistentPushRequestQueueAutoStart(maxRetry: Short, pushActorRef: ActorRef, maxQueueSize: Int, persistenceId: String)
  extends PersistentPushRequestQueue(maxRetry, pushActorRef, maxQueueSize, persistenceId) {
  import PushRequestQueue.StartStream
  import PersistentBufferJournalProtocol._

  override def recovering: Receive = {
    val receive: Receive = {
      case msg@RecoveryComplete(temp) => {
        super.recovering(msg)
        self ! StartStream
      }
    }
    receive orElse super.recovering
  }
}

object PersistentPushRequestQueueAutoStart {
  def props(maxRetry: Short, pushActorRef: ActorRef, maxQueueSize: Int, persistenceId: String) = Props(new PersistentPushRequestQueueAutoStart(maxRetry, pushActorRef, maxQueueSize, persistenceId))
}