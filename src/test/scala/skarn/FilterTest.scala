package skarn
package filter

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import skarn.push.{GCMInfo, PushService, APNSInfo}
import skarn.routing.ErrorFormat
import org.scalatest.{MustMatchers, WordSpecLike}
import spray.http.HttpHeaders.{RawHeader}
import spray.http.{ContentTypes}

/**
 * Created by yusuke on 15/07/01.
 */

class FilterTest extends TestKit(ActorSystem("FilterTest", ConfigFactory.empty()))
with WordSpecLike with MustMatchers {
  "AuthTokenFilter" must {
    val service = PushService("serviceName", "authToken", APNSInfo("apnsCertPath", "apnsPass"), GCMInfo("gcmApiKey"))
    val `X-AUTH-TOKEN` = RawHeader("X-AUTH-TOKEN", service.authToken)

    "X-AUTH-TOKENをもとにサービスをチェックする" in {
      import FilterProtocol._
      import spray.http.HttpHeaders._
      val filterResult = system.actorOf(FilterResultActor.props(testActor))
      val authFilter = system.actorOf(AuthTokenFilter.props(filterResult, testActor, token => token match {
        case "authToken" => Some(service)
        case _ => None
      }))
      val chl = CheckedHeaderList(List(`Content-Type`(ContentTypes.`application/json`)))
      authFilter ! FilterResult(chl, None)
      expectMsg(ErrorFormat.NO_ACCESS_TOKEN)
      authFilter ! FilterResult(CheckedHeaderList(`X-AUTH-TOKEN` :: chl.headers) , None)
      expectMsg(service)
    }
  }
}