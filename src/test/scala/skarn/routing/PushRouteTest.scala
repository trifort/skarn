package skarn
package routing

/**
 * Created by yusuke on 15/06/23.
 */

import com.typesafe.config.ConfigFactory
import skarn.push.PushRequestHandleActorJsonFormat
import skarn.push.PushRequestHandleActorProtocol.PushRequest
import org.scalatest.{MustMatchers, WordSpecLike}
import spray.http.{ContentTypes, HttpEntity}
import spray.routing.Directives._
import spray.testkit.ScalatestRouteTest
import spray.http.StatusCodes._

object PushRoute {
  val route = pathPrefix("v1") {
    path("push") {
      post {
        import PushRequestHandleActorJsonFormat._
        import spray.httpx.SprayJsonSupport._
        entity(as[PushRequest]) { req =>
          respondWithStatus(202) {
            complete {
              "pushing"
            }
          }
        }
      }
    }
  }
}

class PushRouteTest extends WordSpecLike
with MustMatchers with ScalatestRouteTest {

  override def testConfig = ConfigFactory.empty()

  def actorRefFactory = system


  "/v1/push" must {
    "POSTに対してAcceptedを返す" in {
      import ContentTypes._
      import spray.json._
      import PushRequestHandleActorJsonFormat._
      Post("/v1/push", HttpEntity(`application/json`, PushRequest(Nil).toJson.prettyPrint)) ~> PushRoute.route ~> check {
        status must be(Accepted)
        responseAs[String] must be("pushing")
      }
    }
  }
}
