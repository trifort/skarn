package skarn
package push

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import skarn.filter.{FilterResultActor, FilterEntryBase, AuthTokenFilter}
import skarn.definition.{PlatformJsonProtocol, Platform}
import skarn.push.PushRequestHandleActorProtocol.{PushRequest, PushEntity}
import skarn.push.PushRequestQueue.{QueueRequest, Append}
import skarn.routing.{ErrorResponseProtocol, ErrorFormat}
import spray.http.StatusCodes
import spray.json._
import spray.routing.RequestContext

/**
 * Created by yusuke on 2015/05/01.
 */


object PushSupervisorProtocol {
  case class Processing(service: String, total: Int)
}

object PushSupervisorJsonProtocol extends DefaultJsonProtocol {
  import PushSupervisorProtocol._
  implicit val ProcessingFormat = jsonFormat2(Processing)
}


class PushSupervisor(responder: ActorRef, pushRouterSupervisor: Map[String, ActorRef], atomicInteger: AtomicInteger) extends Actor with ActorLogging {
  import PushActorProtocol._
  import PushSupervisorProtocol._
  import PushRequestHandleActorProtocol._

  def receive: Receive =  {
    case PushPayload(PushRequest(notifications), service) => {
      val pushRouterSupervisorRef = pushRouterSupervisor(service.name)
      notifications.foreach { pushEntity =>
        // GCMのマルチキャストの上限が１０００なので１０００づつ送る
        pushEntity.deviceTokens.grouped(1000).foreach { tokens =>
          val id = atomicInteger.incrementAndGet()
          log.info("id = {}", id)
          pushRouterSupervisorRef forward Append(QueueRequest(id, pushEntity.copy(deviceTokens = tokens)))
        }
      }
      val total = notifications.length
      log.info("sending {} push notifications", total)
      responder ! Processing(service.name, total)
    }
  }
}

object PushSupervisor {
  val atomicId = new AtomicInteger()
  def props(responder: ActorRef, pushRouterSupervisor: Map[String, ActorRef]) = Props(new PushSupervisor(responder, pushRouterSupervisor, atomicId))
}


object PushRequestHandleActorProtocol {
  type ExtraData = List[Ex]
  case class PushRequest(notifications: List[PushEntity])
  case class Ex(key: String, value: String)
  case class PushEntity(deviceTokens: Vector[String], platform: Platform, title: Option[String], body: Option[String], badge: Option[Int] = None, sound: Option[String] = None, collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, data: Option[List[Ex]] = None)
  case class PushPayload(request: PushRequest, service: PushService)
}

object PushRequestHandleActorJsonFormat extends DefaultJsonProtocol {
  import PlatformJsonProtocol._
  import PushRequestHandleActorProtocol._
  implicit object ExListJsonFormat extends RootJsonFormat[List[Ex]] {
    def write(exList: List[Ex]): JsValue = {
      val fields = exList.foldRight(Map[String, JsValue]()){ (ex, acc) =>
        acc + ((ex.key, JsString(ex.value)))
      }
      JsObject(fields)
    }
    def read(json: JsValue): List[Ex] = json match {
      case JsArray(elements) => elements.collect{
        case JsObject(fields) => (fields.get("key"), fields.get("value")) match {
          case (Some(JsString(key)), Some(JsString(value))) => Ex(key, value)
          case x => deserializationError("field must be key and value string")
        }
      }.toList
      case x => deserializationError("Expected ExList as JsArray, but got " + x)
    }
  }
  implicit val PushEntityFormat = jsonFormat10(PushEntity)
  implicit val PushRequestFormat = jsonFormat1(PushRequest)

}


class PushResponder(ctx: RequestContext, baseActorRef: ActorRef) extends ResponderBase(ctx, baseActorRef) {
  import PushSupervisorProtocol._
  import ErrorResponseProtocol._
  override def receive = super.receive orElse {
    case response: Processing => {
      import spray.httpx.SprayJsonSupport._
      import PushSupervisorJsonProtocol._
      complete(StatusCodes.Accepted, response)
    }
  }
}

object PushResponder extends ResponderCompanion[PushResponder]


class PushFilterTerminator(target: ActorRef, responder: ActorRef, pushRequest: PushRequest) extends Actor {
  import filter.FilterProtocol._
  import PushRequestHandleActorProtocol._
  val filterResult = context.actorOf(FilterResultActor.props(self))
  val authFilter = context.actorOf(AuthTokenFilter.props(filterResult, responder, PushServiceInfo.findByToken))
  def receive: Receive = {
    case filterResult: FilterResult => {
      authFilter ! filterResult
    }
    case service: PushService => {
      target ! PushPayload(pushRequest, service)
    }
  }
}

object PushFilterTerminator {
  def props(target: ActorRef, responder: ActorRef, pushRequest: PushRequest) = Props(new PushFilterTerminator(target, responder, pushRequest))
}

class PushRequestHandler(val requestContext: RequestContext, pushRouterSupervisor: Map[String, ActorRef], pushRequest: PushRequest) extends FilterEntryBase with ResponderCreator {
  override val responder = createResponder(requestContext, self)
  val pushSupervisor = context.actorOf(PushSupervisor.props(responder, pushRouterSupervisor))
  val pushFilterTerminator = context.actorOf(PushFilterTerminator.props(pushSupervisor, responder, pushRequest))
  val filterActor = pushFilterTerminator

  def createResponder(ctx: RequestContext, baseActorRef: ActorRef) = context.actorOf(PushResponder.props(ctx, baseActorRef))

}

object PushRequestHandler {
  def props(requestContext: RequestContext, pushRouterSupervisor: Map[String, ActorRef], pushRequest: PushRequest) = Props(new PushRequestHandler(requestContext, pushRouterSupervisor, pushRequest))
}