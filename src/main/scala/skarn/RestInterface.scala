/**
 * Created by yusuke on 2015/01/06.
 */

package skarn

import spray.routing._
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor._
import akka.util.Timeout
import skarn.routing._

class RestInterface extends HttpServiceActor with BaseApi {
  def receive = runRoute(routes)
}

object RestInterface {
  def props = Props[RestInterface]
}

trait BaseApi extends HttpService with RouteDefinitions with ActorLogging { actor: Actor =>

  implicit val timeout = Timeout(5 seconds)
  implicit val dispatcher = context.dispatcher
  object IndexRoute extends IndexRoute(context)
  object PushRoute extends PushRoute(context, log.info)


  val routeDefinitions = Seq(
    IndexRoute,
    PushRoute
  )

  override def timeoutRoute: Route = {
    import spray.http.StatusCodes._
    import ErrorResponseProtocol._
    complete(InternalServerError, ErrorFormat.TIMEOUT.response)
  }
}