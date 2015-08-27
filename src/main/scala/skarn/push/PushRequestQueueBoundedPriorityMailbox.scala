package skarn.push

import akka.actor.{PoisonPill, ActorSystem}
import akka.dispatch.{BoundedPriorityMailbox, PriorityGenerator}
import com.typesafe.config.Config
import scala.concurrent.duration._
import skarn.push.PushRequestQueue._

/**
 * Created by yusuke on 15/08/27.
 */

class PushRequestQueueBoundedPriorityMailbox(capacity: Int)
  extends BoundedPriorityMailbox(PriorityGenerator {
    case StartStream => 0
    case _: Done => 0
    case _: Retry => 1
    case PoisonPill => 3
    case _ => 2
  }, capacity, 0 seconds) {

  def this(settings: ActorSystem.Settings, config: Config) = this(config.getInt("mailbox-capacity"))
}