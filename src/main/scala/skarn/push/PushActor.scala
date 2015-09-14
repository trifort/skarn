package skarn
package push

import java.nio.ByteOrder
import akka.actor._
import akka.routing.{DefaultResizer, SmallestMailboxPool}
import skarn.push.GCMProtocol.Notification
import skarn.push.PushRequestHandleActorProtocol.ExtraData
import skarn.push.PushRequestQueue.{Command, QueueRequest}
import scala.concurrent.Promise
import scala.util.{Failure, Success}
import definition.Platform

/**
 * Created by yusuke on 2015/04/30.
 */


object PushActorProtocol {
  case class IosPush(deviceTokens: Vector[String], title: Option[String], body: Option[String], badge: Option[Int] = None, sound: Option[String] = None)
  case class AndroidPush(deviceTokens: Vector[String], title: Option[String], body: Option[String], collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, extend: Option[ExtraData] = None)
  case class IosPushWrap(id: Long, promise: Promise[Command], iosPush: IosPush, start: Option[Long] = None)
  case class AndroidPushWrap(id: Long, promise: Promise[Command], androidPush: AndroidPush, start: Option[Long] = None)
}

class PushIosActor(val service: ApnsService) extends Actor with IosPushStreamProvider with ActorLogging {
  import PushActorProtocol._
  import PushRequestQueue._

  implicit val system = context.system
  implicit val executionContext = context.dispatcher

  implicit val pushService = service
  implicit val order = ByteOrder.BIG_ENDIAN

  def receive: Receive = {
    case IosPushWrap(id, promise, IosPush(deviceTokens, title, body, badge, sound), start) => {
      val cachedLog = log
      val timestamp = start.map(s => s", passed ${System.nanoTime() - s}ns").getOrElse("")
      cachedLog.info("[id:{}] sending APNS request request {}", id, timestamp)
      send(deviceTokens, title, body, badge, sound).onComplete {
        case Success(_) => {
          val timestamp = start.map(s => s", passed ${System.nanoTime() - s}ns").getOrElse("")
          cachedLog.info("[id:{}] APNS request is completed {}", id, timestamp)
          promise.success(Done(id, start))
        }
        case Failure(e) => {
          cachedLog.error(e, "[id:{}] APNS connection is closed", id)
          promise.success(Retry(id))
        }
      }
    }
  }
}

object PushIosActor {
  def props(service: ApnsService) = Props(new PushIosActor(service))
}

class PushIosRouter(routeeNum: Int, service: ApnsService) extends Actor {
  val routerProps = SmallestMailboxPool(1).withResizer(DefaultResizer(1, routeeNum, 1, 0.5, 0.2, 0.1, 3))
    .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)
    .props(PushIosActor.props(service))

  val router = context.actorOf(routerProps)

  def receive: Receive = {
    case m => {
      router forward m
    }
  }
}

object PushIosRouter {
  def props(routeeNum: Int, service: ApnsService) = Props(new PushIosRouter(routeeNum, service))
}

class PushAndroidActor(val apiKey: String) extends Actor with ActorLogging with AndroidPushStreamProvider {
  import PushRequestQueue._
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  import PushActorProtocol._
  def receive: Receive = {
    case AndroidPushWrap(id, promise, AndroidPush(deviceToken, title, body, collapseKey, delayWhileIdle, timeToLive, extend), start) => {
      val cachedLog = log
      val timestamp = start.map(s => s", passed ${System.nanoTime() - s}ns").getOrElse("")
      cachedLog.info("[id:{}] sending GCM request {}", id, timestamp)
      send(deviceToken, Some(Notification(title, body)), collapseKey, delayWhileIdle, timeToLive, extend).onComplete{
        case Success(result) => {
          val timestamp = start.map(s => s", passed ${System.nanoTime() - s}ns").getOrElse("")
          cachedLog.info("[id:{}] GCM request is completed; success: {}, failure: {} {}", id, result.success, result.failure, timestamp)
          /*
           * Fixme: If `result.failure` is not zero, resend the failed subset of all tokens.
           */
          promise.success(Done(id, start))
        }
        case Failure(e) => {
          promise.success(Retry(id))
          cachedLog.error(e, "[id:{}] GCM request failed. Retrying...", id)
        }
      }
    }
  }
}

object PushAndroidActor {
  def props(apiKey: String) = Props(new PushAndroidActor(apiKey))
}

class PushPlatformRouter(val apnsService: ApnsService, val apiKey: String) extends Actor
with PlatformActorCreator with ActorLogging {
  import PushActorProtocol._
  import PushRequestHandleActorProtocol._
  import PushFlow._

  def receive: Receive = {
    case PushEntityWrap(QueueRequest(id, entity@PushEntity(_, platform, _, _, _, _, _, _, _, _), start, _), promise) => {
      import  Platform._
      platform match {
        case Ios => ios forward IosPushWrap(id, promise, IosPush(entity.deviceTokens, entity.title, entity.body, entity.badge, entity.sound), start)
        case Android => android forward AndroidPushWrap(id, promise, AndroidPush(entity.deviceTokens, entity.title, entity.body, entity.collapseKey, entity.delayWhileIdle, entity.timeToLive, entity.data), start)
        case Unknown => log.warning("unrecognized platform received")
      }
    }
  }
}

object PushPlatformRouter {
  def props(apnsService: ApnsService, apiKey: String) = Props(new PushPlatformRouter(apnsService, apiKey))
}

trait PlatformActorCreator { this: Actor =>
  val apnsService: ApnsService
  val apiKey: String
  lazy val ios = context.actorOf(PushIosRouter.props(8, apnsService))
  lazy val android = context.actorOf(PushAndroidActor.props(apiKey))
}

class PushRouterSupervisor(serviceName: String, routerProps: Props) extends Actor {
  import PushFlow._

  lazy val router = context.actorOf(routerProps, s"$serviceName-pushRouter")

  def receive: Receive = {
    case message: PushEntityWrap => {
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
