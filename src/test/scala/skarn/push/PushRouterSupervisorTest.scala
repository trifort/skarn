package skarn.push

import akka.actor.{Actor, Props, ActorSystem}
import akka.routing.SmallestMailboxPool
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import skarn.definition.Platform
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.push.PushFlow.PushEntityWrap
import skarn.push.PushRequestQueue.{Command, QueueRequest}
import scala.concurrent.Promise
import scala.concurrent.duration._

/**
 * Created by yusuke on 15/07/06.
 */
class PushRouterSupervisorTest extends TestKit(ActorSystem({"PushPlatformActorTest"}, ConfigFactory.empty()
)) with WordSpecLike with MustMatchers { testSelf =>

  class SpyActor extends Actor {
    def receive: Receive = {
      case msg => testActor forward msg
    }
  }

  object SpyActor {
    def props() = Props(new SpyActor)
  }

  trait TestPushRouterCreator extends PushRouterCreator {
    def createRouter(): Props = {
      SmallestMailboxPool(100).props(SpyActor.props())
    }
  }

  class TestPushRouterSupervisor extends PushRouterSupervisor("Test", SmallestMailboxPool(100).props(SpyActor.props())) with TestPushRouterCreator

  object TestPushRouterSupervisor {
    def props() = Props(new TestPushRouterSupervisor)
  }

  "PushRouterSupervisor" must {

    val testPushRouterSupervisor = system.actorOf(TestPushRouterSupervisor.props(), "testPushRouterSupervisor")

    "load balance with router. (Currently no load balancing with router. Just forwarding to child actor)" in {
      import skarn.push.PushRequestHandleActorProtocol._
      val promise = Promise[Command]
      Stream.from(1).take(100).map {i =>
        val platform = Platform(i % 2 + 1)
        PushEntityWrap(QueueRequest(0, PushEntity(Vector("deviceToken"), platform, Some("message"), None)), promise)
      }.foreach(testPushRouterSupervisor ! _)
      receiveN(100, 5 seconds).map {
        case PushEntityWrap(QueueRequest(0, PushEntity(Vector("deviceToken"), Platform.Ios, Some("message"), _, _, _, _, _, _, _), _, _), _) => (1, 0)
        case PushEntityWrap(QueueRequest(0, PushEntity(Vector("deviceToken"), Platform.Android, Some("message"), _, _, _, _, _, _, _), _, _), _) => (0, 1)
        case _ => (0, 0)
      }.reduce { (a, b) =>
        (a._1 + b._1, a._2 + b._2)
      } must be (50, 50)
    }
  }

}
