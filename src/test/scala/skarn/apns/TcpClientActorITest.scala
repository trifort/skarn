package skarn.apns

import akka.actor._
import akka.io.Tcp
import akka.testkit.{TestProbe, TestKit}
import akka.util.ByteString
import kamon.Kamon
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.TcpServerHandler.TestSend
import skarn.{TcpServerTestSetupBase, StopSystemAfterAllWithAwaitTermination}
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.duration._

/**
* Created by yusuke on 15/09/14.
*/


object WatchActor {
  def props(target: ActorRef, reportTo: ActorRef) = Props(new WatchActor(target, reportTo))
}

class WatchActor(target: ActorRef, reportTo: ActorRef) extends Actor {
  context.watch(target)
  def receive = {
    case msg: Terminated => reportTo ! "terminated"
  }
}

class TcpClientActorITest extends TestKit({Kamon.start(); ActorSystem("TcpClientActorITest")})
with WordSpecLike with MustMatchers with StopSystemAfterAllWithAwaitTermination { testSelf =>

  "TcpClientActor" must {
    import Tcp._
    import TcpClientActorProtocol._
    val trashbox = TestProbe()
    "send a request" in {

      new TcpServerTestSetupBase(system, testActor) {
        import system.dispatcher

        val connection = expectMsgClass(classOf[Bound])
        val client = system.actorOf(TcpClientActor.props(connection.localAddress, 5 seconds))

        expectMsgClass(classOf[Connected])
        expectMsgClass(classOf[Register])

        val p1 = Promise[Unit]
        client ! Send(ByteString("abcde"), p1, trashbox.ref)
        expectMsg(Received(ByteString("abcde")))

        val p2 = Promise[Unit]
        client ! Send(ByteString("abcde"), p2, trashbox.ref)
        expectMsg(Received(ByteString("abcde")))

        val p3 = Promise[Unit]
        client ! Send(ByteString("abcde"), p3, trashbox.ref)
        expectMsg(Received(ByteString("abcde")))

        Await.result(Future.sequence(List(p1, p2, p3).map(_.future)), 5 seconds) must be(List.fill(3)(()))

        serverActor ! PoisonPill
      }
    }

    "receive a response" in {
      new TcpServerTestSetupBase(system, testActor) {

        val connection = expectMsgClass(classOf[Bound])
        val publisher = TestProbe()
        val client = system.actorOf(TcpClientActor.props(connection.localAddress, 5 seconds))

        expectMsgClass(classOf[Connected])
        val handler = expectMsgClass(classOf[Register]).handler

        handler ! TestSend(ByteString("abcde"))

        publisher.expectMsg(Receive(ByteString("abcde")))

        serverActor ! PoisonPill
      }
    }

    "stop when connection is closed" in {
      new TcpServerTestSetupBase(system, testActor) {

        val connection = expectMsgClass(classOf[Bound])
        val publisher = TestProbe()
        val client = system.actorOf(TcpClientActor.props(connection.localAddress, 5 seconds))

        system.actorOf(WatchActor.props(client, testActor))

        expectMsgClass(classOf[Connected])
        val handler = expectMsgClass(classOf[Register]).handler

        handler ! Close

        expectMsg("terminated")

        serverActor ! PoisonPill
      }
    }
  }

}
