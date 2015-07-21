/**
* Created by noguchi on 2015/02/08.
*/
package skarn

import akka.actor.{ActorRef, PoisonPill, Props, Actor}
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.httpx.marshalling.ToResponseMarshaller
import spray.routing.RequestContext
import skarn.routing.{ErrorResponse, ErrorResponseProtocol, ErrorFormat, ErrorDetail}
import spray.json._


class Responder(requestContext:RequestContext, baseActorRef: ActorRef) extends Actor {
  import spray.httpx.SprayJsonSupport._

  def receive = {
    //エラー発生時
    case err@ErrorDetail(_, _, _)        => {
      import skarn.routing.ErrorResponseProtocol._
      requestContext.complete(BadRequest, err)
      baseActorRef ! PoisonPill
    }
    case msg => {
      requestContext.complete("")
      baseActorRef ! PoisonPill
    }
  }
}

object Responder {
  def props(requestContext: RequestContext, baseActorRef: ActorRef) = Props(new Responder(requestContext, baseActorRef))
}

trait ResponderCreator {
  def createResponder(ctx: RequestContext, baseActorRef: ActorRef): ActorRef
}

abstract class ResponderBase(requestContext:RequestContext, baseActorRef: ActorRef) extends Actor {
  def complete[T](value: T)(implicit marshaller: ToResponseMarshaller[T]) = {
    requestContext.complete(value)(marshaller)
    baseActorRef ! PoisonPill
  }
  def complete[T](status: StatusCode, value: T)(implicit marshaller: ToResponseMarshaller[(StatusCode, T)]) = {
    requestContext.complete(status, value)(marshaller)
    baseActorRef ! PoisonPill
  }
  def completeWithError(error: ErrorFormat) = {
    import ErrorResponseProtocol._
    complete(error.status, error.response)
    baseActorRef ! PoisonPill
  }

  def receive: Receive = {
    case msg: ErrorFormat => {
      completeWithError(msg)
    }
    case msg: ErrorResponse => {
      import spray.http.StatusCodes._
      completeWithError(ErrorFormat(msg, BadRequest))
    }
    case msg: ErrorDetail => {
      import spray.http.StatusCodes._
      completeWithError(ErrorFormat(ErrorResponse(List(msg)), BadRequest))
    }
  }
}

trait ResponderCompanion[T <: ResponderBase] {
  def props(requestContext:RequestContext, baseActorRef: ActorRef)(implicit R: reflect.ClassTag[T]) = Props(R.runtimeClass, requestContext, baseActorRef)
}