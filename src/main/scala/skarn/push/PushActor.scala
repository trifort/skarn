package skarn
package push

import akka.actor.{ActorLogging, Props, Actor}
import kamon.Kamon
import skarn.push.GCMProtocol.Notification
import skarn.push.PushRequestHandleActorProtocol.ExtraData
import scala.util.{Failure, Success}
import com.notnoop.apns.ApnsService
import definition.Platform

/**
 * Created by yusuke on 2015/04/30.
 */


object PushActorProtocol {
  case class IosPush(deviceTokens: Vector[String], title: Option[String], body: Option[String], badge: Option[Int] = None, sound: Option[String] = None)
  case class AndroidPush(deviceTokens: Vector[String], title: Option[String], body: Option[String], collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, extend: Option[ExtraData] = None)
}

class PushIosActor(val service: ApnsService2) extends Actor with IosPushStreamProvider with ActorLogging {
  import PushActorProtocol._

  implicit val system = context.system
  implicit val executionContext = context.dispatcher

  implicit val pushService = service
  def receive: Receive = {
    case IosPush(deviceTokens, title, body, badge, sound) => {
      val cachedLog = log
      send(deviceTokens, title, body, badge, sound).onComplete {
        case Success(result) => {
          cachedLog.info("APNS result; success: {}, failure: {}", result.toString)
        }
        case Failure(e) => {
          cachedLog.error(e, "APNS request failed")
        }
      }
    }
  }
}

object PushIosActor {
  def props(service: ApnsService2) = Props(new PushIosActor(service))
}

class PushAndroidActor(val apiKey: String) extends Actor with ActorLogging with AndroidPushStreamProvider {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  import PushActorProtocol._
  def receive: Receive = {
    case AndroidPush(deviceToken, title, body, collapseKey, delayWhileIdle, timeToLive, extend) => {
      val cachedLog = log
      val gcmTrace = Kamon.tracer.newContext("gcm-trace")
      send(deviceToken, Some(Notification(title, body)), collapseKey, delayWhileIdle, timeToLive, extend).onComplete{
        case Success(result) => {
          gcmTrace.finish()
          cachedLog.info("GCM result; success: {}, failure: {}", result.success, result.failure)
        }
        case Failure(e) => {
          gcmTrace.finish()
          cachedLog.error(e, "GCM request failed")
        }
      }
    }
  }
}

object PushAndroidActor {
  def props(apiKey: String) = Props(new PushAndroidActor(apiKey))
}

class PushPlatformRouter(val apnsService: ApnsService2, val apiKey: String) extends Actor
with PlatformActorCreator with ActorLogging {
  import PushActorProtocol._
  import PushRequestHandleActorProtocol._

  def receive: Receive = {
    case entity@PushEntity(_, platform, _, _, _, _, _, _, _, _) => {
      import  Platform._
      platform match {
        case Ios => ios forward IosPush(entity.deviceTokens, entity.title, entity.body, entity.badge, entity.sound)
        case Android => android forward AndroidPush(entity.deviceTokens, entity.title, entity.body, entity.collapseKey, entity.delayWhileIdle, entity.timeToLive, entity.data)
        case Unknown => log.warning("unrecognized platform received")
      }
    }
  }
}

object PushPlatformRouter {
  def props(apnsService: ApnsService2, apiKey: String) = Props(new PushPlatformRouter(apnsService, apiKey))
}

trait PlatformActorCreator { this: Actor =>
  val apnsService: ApnsService2
  val apiKey: String
  lazy val ios = context.actorOf(PushIosActor.props(apnsService))
  lazy val android = context.actorOf(PushAndroidActor.props(apiKey))
}

class PushRouterSupervisor(serviceName: String, routerProps: Props) extends Actor {
  import PushRequestHandleActorProtocol._

  lazy val router = context.actorOf(routerProps, s"$serviceName-pushRouter")

  def receive: Receive = {
    case message: PushEntity => {
      router forward message
    }
  }
}

object PushRouterSupervisor {
  def props(serviceName: String, routerProps: Props) = Props(new PushRouterSupervisor(serviceName, routerProps))
}

trait PushRouterCreator {
  def createRouter(): Props
}
