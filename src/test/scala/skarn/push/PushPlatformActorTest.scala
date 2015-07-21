package skarn
package push

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestProbe, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import definition.Platform

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
      val testPushPlatformRouter = system.actorOf(TestPushPlatformRouter.props())
      testPushPlatformRouter ! PushEntity(Vector("deviceToken"), Platform.Ios, "ios")
      iosProbe1.expectMsg(IosPush(Vector("deviceToken"), "ios"))
      testPushPlatformRouter ! PushEntity(Vector("deviceToken"), Platform.Android, "android")
      androidProbe1.expectMsg(AndroidPush(Vector("deviceToken"), "android"))
    }
  }
}