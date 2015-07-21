package skarn.filter

import akka.actor.{Props, ActorRef, Actor}
import skarn.push.{PushService, PushServiceInfo}
import skarn.routing.ErrorFormat
import spray.routing.RequestContext
import akka.util.Timeout
import scala.concurrent.duration._
/**
 * Created by yusuke on 15/06/29.
 */

trait FilterEntryBase extends Actor {
  import FilterProtocol._
  implicit lazy val timeout = Timeout(5 seconds)

  val requestContext: RequestContext

  val responder: ActorRef
  val filterActor: ActorRef

  def receive = {
    case msg: CheckedHeaderList => {
      filterActor ! FilterResult(msg, None)
    }
  }
}

object FilterProtocol {
  type Result = PushService
  case class CheckedHeaderList(headers: List[spray.http.HttpHeader])
  case class FilterResult(checkedHeader: CheckedHeaderList, result: Option[Result])
}

class FilterResultActor(sendTo: ActorRef) extends Actor {
  import FilterProtocol._
  def receive: Receive = {
    case FilterResult(_, result) => {
      sendTo ! result.get
    }
  }
}

object FilterResultActor {
  def props(sendTo: ActorRef) = Props(new FilterResultActor(sendTo))
}

class AuthTokenFilter(sendTo: ActorRef, rejectedTo: ActorRef, serviceFinder: String => Option[PushService]) extends Actor {
  import FilterProtocol._
  def receive: Receive = {
    case FilterResult(chl@CheckedHeaderList(headers), _) => {
      headers.find(_.lowercaseName == "x-auth-token") match {
        case Some(header) => {
          serviceFinder(header.value) match {
            case Some(service) => {
              sendTo ! FilterResult(chl, Some(service))
            }
            case None => rejectedTo ! ErrorFormat.INVALID_PUSH_ACCESS_TOKEN
          }
        }
        case None => rejectedTo ! ErrorFormat.NO_ACCESS_TOKEN
      }
    }
  }
}

object AuthTokenFilter {
  def props(sendTo: ActorRef, rejectedTo: ActorRef, serviceFinder: String => Option[PushService]) = Props(new AuthTokenFilter(sendTo, rejectedTo, serviceFinder))
}