package skarn.routing

import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import spray.http.HttpHeaders.RawHeader
import spray.testkit.ScalatestRouteTest
import spray.routing.Directives._

/**
 * Created by yusuke on 15/06/12.
 */
class HttpMethodOverrideTest extends  WordSpecLike
with MustMatchers with ScalatestRouteTest {
  import HttpMethodOverride._

  override def testConfig = ConfigFactory.empty()

  val route = path("HttpMethodOverride") {
    oput {
      complete("oput")
    } ~
    odelete {
      complete("odelete")
    } ~
    opatch {
      complete("opatch")
    }
  }

  "HttpMethodOverride" must {
    "put" in {
      Post("/HttpMethodOverride") ~> RawHeader("X-HTTP-Method-Override", "PUT") ~> route ~> check {
        responseAs[String] must be("oput")
      }
    }
    "delete" in {
      Post("/HttpMethodOverride") ~> RawHeader("X-HTTP-Method-Override", "DELETE") ~> route ~> check {
        responseAs[String]  must be("odelete")
      }
    }
    "patch" in {
      Post("/HttpMethodOverride") ~> RawHeader("X-HTTP-Method-Override", "PATCH") ~> route ~> check {
        responseAs[String]  must be("opatch")
      }
    }
  }
}
