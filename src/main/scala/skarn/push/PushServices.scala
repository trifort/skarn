package skarn
package push

import akka.actor.{SupervisorStrategy, ActorRef, Props, ActorContext}
import akka.routing.{DefaultResizer, SmallestMailboxPool}
import com.notnoop.apns.ApnsService

/**
 * Created by yusuke on 15/07/06.
 */


trait PushServices {
  val actorContext: ActorContext
  lazy val services: Map[String, ActorRef] = PushServiceInfo.services.map {pushService =>
    val apiKey = pushService.gcm.apiKey
    val apnsService = new IosPushProvider with IosProductionPushService {
      val password = pushService.apns.password
      val certificate = Apns.loadCertificateFromFile(pushService.apns.certPath)
    }
    val props = SmallestMailboxPool(1)
      .withResizer(DefaultResizer(1, 100, 1, 0.5, 0.2, 0.1, 3))
      .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)
      .props(PushPlatformRouter.props(apnsService.service, apiKey))
    val actorRef = actorContext.actorOf(PushRouterSupervisor.props(pushService.name, props), pushService.name)
    (pushService.name, actorRef)
  }.toMap
}