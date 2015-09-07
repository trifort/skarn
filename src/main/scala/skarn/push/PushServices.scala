package skarn
package push

import java.io.FileNotFoundException
import akka.actor._
import scala.util.{Success, Failure}

/**
 * Created by yusuke on 15/07/06.
 */

@SerialVersionUID(1L)
class InvalidAPNSCertificateError(msg: String) extends Error(msg) with Serializable

trait PushServices {
  val context: ActorContext
  val services: Map[String, ActorRef] = PushServiceInfo.services.map {pushService =>
    val apiKey = pushService.gcm.apiKey
    val apnsService = new ApnsService {
      val password = pushService.apns.password
      val certificate = Apns.loadCertificateFromFile(pushService.apns.certPath) match {
        case Success(file) => file
        case Failure(e: FileNotFoundException) => throw new InvalidAPNSCertificateError(s"Certificate ${pushService.apns.certPath} is not found")
        case Failure(e) => {
          throw new InvalidAPNSCertificateError("Invalid certificate")
        }
      }
    }
    val props = PushPlatformRouter.props(apnsService, apiKey)
    val pushActorRef = context.actorOf(PushRouterSupervisor.props(pushService.name, props), pushService.name)
    val persistenceId = s"queue-${pushService.name}"
    val pushRequestQueue = context.actorOf(PersistentPushRequestQueueAutoStart.props(3, pushActorRef, context.system.settings.config.getInt("application.max-queue-size"), persistenceId), persistenceId)
    (pushService.name, pushRequestQueue)
  }.toMap
}