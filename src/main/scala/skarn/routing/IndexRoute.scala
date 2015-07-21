package skarn
package routing

/**
 * Created by yusuke on 2015/03/04.
 */

import akka.actor.ActorContext
import skarn.push.PushServiceInfo
import spray.routing._
import Directives._
import spray.http.MediaTypes._

class IndexRoute(context: ActorContext) extends BasicRoute {
  implicit val system = context.system
  val resource = ""

  val route = (pathSingleSlash | path(version)) {
    get {
      complete {
        <html>
          <body>
            <h1>Skarn</h1>
            <p>Push notification server build on Akka Actor with Scala</p>
            <h2>Services</h2>
            <ul>
              {PushServiceInfo.services.map(s => <li>{s.name}</li>)}
            </ul>
          </body>
        </html>
      }
    }
  }
}
