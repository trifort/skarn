package skarn
package routing

/**
 * Created by yusuke on 2015/03/05.
 */
import spray.routing._
import Directives._

/*
 * Routeの定義一覧とそれを合成するトレイト
 */
trait RouteDefinitions {
  val routeDefinitions: Seq[BasicRoute]
  lazy val routes: Route = routeDefinitions.map(_.route).reduce(_ ~ _)
  implicit lazy val rejectionHandler: RejectionHandler = (routeDefinitions :+ BasicErrorHandler).map(_.rejectionHandler).reduce(_ orElse _)
}
