package skarn.routing

/**
 * Created by yusuke on 2015/05/01.
 */

import akka.actor.ActorContext
import skarn.filter.FilterProtocol.CheckedHeaderList
import skarn.push._
import spray.routing.Directives._

class PushRoute(val context: ActorContext) extends BasicRoute with PushServices {
  val resource = "push"

  val route = pathPrefix(version / resource) {
    post {
      import spray.httpx.SprayJsonSupport._
      import PushRequestHandleActorJsonFormat._
      import PushRequestHandleActorProtocol._
      entity(as[PushRequest]) { req =>
        noop { ctx =>
          val pushRequestHandleActor = context.actorOf(PushRequestHandler.props(ctx, services, req))
          pushRequestHandleActor ! CheckedHeaderList(ctx.request.headers)
        }
      }
    }
  }
}