package skarn
package push

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestProbe, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import definition.Platform
import skarn.push.PushFlow.PushEntityWrap
import skarn.push.PushRequestQueue.{Command, QueueRequest}

import scala.concurrent.Promise

/**
 * Created by yusuke on 15/07/06.
 */
class PushPlatformActorTest extends TestKit(ActorSystem({"PushPlatformActorTest"}, ConfigFactory.empty()
)) with WordSpecLike with MustMatchers { testSelf =>

  val iosProbe1 = TestProbe()
  val androidProbe1 = TestProbe()

  trait DummyPlatformActorCreator extends PlatformActorCreator { this: Actor =>
    override lazy val ios = iosProbe1.testActor
    override lazy val android = androidProbe1.testActor
  }

  class TestPushPlatformRouter
    extends PushPlatformRouter(null, "apiKey") with DummyPlatformActorCreator

  object TestPushPlatformRouter {
    def props() = Props(new TestPushPlatformRouter)
  }

  "PushPlatformActor" must {
    "Platformに応じてメッセージを各プラットフォームのPUSHアクターにforwardする" in {
      import PushRequestHandleActorProtocol._
      import PushActorProtocol._
      val promise = Promise[Command]
      val testPushPlatformRouter = system.actorOf(TestPushPlatformRouter.props())
      testPushPlatformRouter ! PushEntityWrap(QueueRequest(0, PushEntity(Vector("deviceToken"), Platform.Ios, None, Some("ios")), None, 0), promise)
      iosProbe1.expectMsg(IosPushWrap(0, promise, IosPush(Vector("deviceToken"), None, Some("ios"))))
      testPushPlatformRouter ! PushEntityWrap(QueueRequest(0, PushEntity(Vector("deviceToken"), Platform.Android, Some("android"), None), None, 0), promise)
      androidProbe1.expectMsg(AndroidPushWrap(0, promise, AndroidPush(Vector("deviceToken"), Some("android"), None)))
    }
  }
}