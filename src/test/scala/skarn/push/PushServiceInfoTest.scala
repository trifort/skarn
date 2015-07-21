package skarn.push

import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}

/**
 * Created by yusuke on 15/07/08.
 */
class PushServiceInfoTest extends WordSpecLike with MustMatchers{

  object PushServiceInfo extends PushServiceInfo {
    val config = ConfigFactory.parseString(
      """
        |{
        |  "services": [
        |    {
        |      "name": "test",
        |      "auth-token": "MjAxNS0wNS0wMVQwOTo0MjozMC4xNjda",
        |      "apns": {
        |        "cert-path": "apns/apns.p12",
        |        "password": "test"
        |      },
        |      "gcm": {
        |        "api-key": "AIzaSyZ-1u...0GBYzPu7Udno5aA"
        |      }
        |    },
        |    {
        |      "name": "test2",
        |      "auth-token": "MjAxNS0wNy0wOFQwMjo0NTowNS43NDFa",
        |      "apns": {
        |        "cert-path": "apns/apns2.p12",
        |        "password": "test2"
        |      },
        |      "gcm": {
        |        "api-key": "AIzaSyZ-1u...0GBYzPu7Udno5aA"
        |      }
        |    }
        |  ]
        |}
      """.stripMargin)
  }

  "PushServiceInfo" must {
    "PushServiceInfo.findByToken return PushService with specified auth token" in {
      PushServiceInfo.findByToken("MjAxNS0wNS0wMVQwOTo0MjozMC4xNjda") must be(Some(
        PushService("test", "MjAxNS0wNS0wMVQwOTo0MjozMC4xNjda", APNSInfo("apns/apns.p12", "test"), GCMInfo("AIzaSyZ-1u...0GBYzPu7Udno5aA"))
      ))
      PushServiceInfo.findByToken("MjAxNS0wNy0wOFQwMjo0NTowNS43NDFa") must be(Some(
        PushService("test2", "MjAxNS0wNy0wOFQwMjo0NTowNS43NDFa", APNSInfo("apns/apns2.p12", "test2"), GCMInfo("AIzaSyZ-1u...0GBYzPu7Udno5aA"))
      ))
    }

    "PushServiceInfo.findByName return PushService with specified name" in {
      PushServiceInfo.findByName("test") must be(Some(
        PushService("test", "MjAxNS0wNS0wMVQwOTo0MjozMC4xNjda", APNSInfo("apns/apns.p12", "test"), GCMInfo("AIzaSyZ-1u...0GBYzPu7Udno5aA"))
      ))
      PushServiceInfo.findByName("test2") must be(Some(
        PushService("test2", "MjAxNS0wNy0wOFQwMjo0NTowNS43NDFa", APNSInfo("apns/apns2.p12", "test2"), GCMInfo("AIzaSyZ-1u...0GBYzPu7Udno5aA"))
      ))
    }
  }
}
