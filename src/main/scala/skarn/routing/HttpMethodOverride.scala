package skarn.routing

import spray.http.HttpMethods
import spray.routing.Directives._
import spray.routing._

/**
 * Created by yusuke on 15/06/12.
 */


//https://youtrack.jetbrains.com/issue/SCL-7100
object HttpMethodOverride {
  val methodOverride: Directive0 = optionalHeaderValueByName("X-HTTP-Method-Override") flatMap {
    case Some(method) => {
      HttpMethods.getForKey(method.toUpperCase()) match {
        case Some(m) => mapRequest(_.copy(method = m))
        case None => noop
      }
    }
    case None => noop
  }

  val oput = methodOverride & put
  val odelete = methodOverride & delete
  val opatch = methodOverride & patch
}