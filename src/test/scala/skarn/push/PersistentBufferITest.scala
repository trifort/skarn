package skarn.push

import akka.actor.{PoisonPill, Props, ActorLogging, ActorSystem}
import akka.testkit.{TestProbe, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import persistence.Cleanup
import scala.concurrent.duration._
import akka.persistence.{RecoveryCompleted, PersistentActor, Update}
/**
 * Created by yusuke on 15/08/31.
 */

case class P(num : Int) extends Persistent

class PersistentBufferITest extends TestKit(ActorSystem("PersistentBufferTest", ConfigFactory.parseString(
  """
    |akka.persistence.journal.leveldb.native=off
  """.stripMargin))
) with WordSpecLike with MustMatchers with Cleanup {

  class PersistentTestActor extends PersistentActor with ActorLogging {
    override def persistenceId = "PersistentBufferTest-1"

    var count = 0

    def receiveCommand = {
      case msg: Persistent => {
        val cachedLog = log
        persist(msg) { evt =>
          count += 1
          cachedLog.info("receiveCommand: {}", evt)
          testActor ! evt
        }
      }
    }

    def receiveRecover: Receive = {
      case _: Persistent => count += 1
      case RecoveryCompleted => testActor ! PersistentJournalActor.RecoveryComplete(count)
    }
  }

  object PersistentTestActor {
    def props = Props(new PersistentTestActor)
  }

  "PersistentBuffer" must {
    "read message from journal" in {
      import PersistentBuffer._
      val persistentTestActor = system.actorOf(PersistentTestActor.props)
      val persistentBuffer = system.actorOf(PersistentBuffer.props(testActor, "PersistentBufferTest-1", "view-1", 10))
      expectMsg(PersistentJournalActor.RecoveryComplete(0))
      1 to 100 map (P(_)) foreach (persistentTestActor ! _)
      expectMsgAllOf(1 to 100 map (P(_)): _*)
      persistentBuffer ! Fill
      persistentBuffer ! Request(1)
      expectMsg(Response(Vector(P(1))))
      persistentBuffer ! Request(1)
      expectMsg(Response(Vector(P(2))))
      persistentBuffer ! Request(3)
      expectMsg(Response((3 to 5).map(v => P(v)).toVector))
      persistentBuffer ! Request(5)
      expectMsg(Response((6 to 10).map(v => P(v)).toVector))
      persistentBuffer ! Request(5)
      expectMsg(Response(Vector(P(11))))
      expectMsg(Response(Vector(P(12))))
      expectMsg(Response(Vector(P(13))))
      expectMsg(Response(Vector(P(14))))
      expectMsg(Response(Vector(P(15))))
      Seq(persistentTestActor, persistentBuffer) foreach (_ ! PoisonPill)
    }

    "recover" in {
      import PersistentBuffer._
      val testProbe = TestProbe()
      val persistentTestActor = system.actorOf(PersistentJournalActor.props("PersistentBufferTest-1", testProbe.ref))
      val persistentBuffer = system.actorOf(PersistentBuffer.props(testProbe.ref, "PersistentBufferTest-1", "view-1", 10))
      testProbe.expectMsg(PersistentJournalActor.RecoveryComplete(100))
      persistentBuffer ! Fill
      persistentBuffer ! Request(10)
      testProbe.expectMsg(Response((1 to 10).map(v => P(v)).toVector))
    }
  }
}
