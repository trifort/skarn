package skarn
package routing

/**
 * Created by yusuke on 2015/03/04.
 */

import spray.routing._

/*
 * ルーティング定義のベース
 */
trait BasicRoute extends ErrorHandler {
  val route: Route
  val resource: String
  val versionNumber = 1
  val version = s"v$versionNumber"
}
