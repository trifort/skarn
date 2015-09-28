package skarn.apns

import akka.actor.{PoisonPill, ActorSystem}
import akka.io.Tcp
import akka.stream.ActorMaterializer
import akka.stream.testkit.scaladsl.TestSource
import akka.testkit.{TestProbe, TestKit}
import akka.stream.scaladsl.{Keep, Source, Sink}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.{TcpServerTestSetupBase, StopSystemAfterAllWithAwaitTermination}
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._

/**
* Created by yusuke on 15/09/15.
*/

class TcpStreamActorITest  extends TestKit({Kamon.start(); ActorSystem("TcpStreamActorITest", ConfigFactory.empty())})
with WordSpecLike with MustMatchers with StopSystemAfterAllWithAwaitTermination { testSelf =>
  "TcpStreamActor" must {
    import Tcp._
    import TcpClientActorProtocol._
    "send a request with Stream" in {
      val serverProbe = TestProbe()
      new TcpServerTestSetupBase(system, serverProbe.ref) {
        import ConnectionPool.Implicits._

        implicit val m = ActorMaterializer()

        val connection = serverProbe.expectMsgClass(classOf[Bound])

        val connectionPool = ConnectionPool.create(TcpConnectionPoolSettings(connection.localAddress, 10, 1, 5 seconds))
        connectionPool.startConnection()

        val clientSink = Sink.actorSubscriber(TcpStreamActor.props(connectionPool, 10))

        val probe = TestSource.probe[Send].toMat(clientSink)(Keep.left).run()

        serverProbe.expectMsgClass(classOf[Connected])
        serverProbe.expectMsgClass(classOf[Register])

        1 to 100 foreach { _ =>
          val p = Promise[Unit]
          probe.sendNext(Send(ByteString("abcde"), p))
          serverProbe.expectMsg(Received(ByteString("abcde")))
        }

        serverActor ! PoisonPill
      }
    }

    "recreate connection when closed" in {
      val serverProbe = TestProbe()
      new TcpServerTestSetupBase(system, serverProbe.ref) {
        import system.dispatcher
        import ConnectionPool.Implicits._

        val nOfConnection = 1
        implicit val m = ActorMaterializer()

        val connection = serverProbe.expectMsgClass(classOf[Bound])

        val connectionPool = ConnectionPool.create(TcpConnectionPoolSettings(connection.localAddress, 2, nOfConnection, 5 seconds))
        connectionPool.startConnection()

        val clientSink = Sink.actorSubscriber(TcpStreamActor.props(connectionPool, 2))

        val probe = TestSource.probe[Send].toMat(clientSink)(Keep.left).run()

        val (connectMsg, registerMsg) = serverProbe.receiveN(nOfConnection * 2).partition {
          case _: Connected => true
          case _: Register => false
        }.asInstanceOf[(Seq[Connected], Seq[Register])]
        connectMsg.length must be (nOfConnection)
        registerMsg.length must be (nOfConnection)

        val handler = registerMsg.head.handler

        val responses1 = 1 to 20 map { _ =>
          val p = Promise[Unit]
          probe.sendNext(Send(ByteString("abcde"), p))
          p.future
        }

        handler ! ConfirmedClose

        val responses2 = 21 to 100 map { _ =>
          val p = Promise[Unit]
          probe.sendNext(Send(ByteString("abcde"), p))
          Thread.sleep(10)
          p.future
        }

        Await.result(Future.sequence(responses1 ++ responses2), 6 seconds).length must be (100)

        serverActor ! PoisonPill
      }
    }

    "recreate connection when forcibly closed" in {
      val serverProbe = TestProbe()
      new TcpServerTestSetupBase(system, serverProbe.ref) {
        import system.dispatcher
        import ConnectionPool.Implicits._

        val nOfConnection = 1
        implicit val m = ActorMaterializer()

        val connection = serverProbe.expectMsgClass(classOf[Bound])

        val connectionPool = ConnectionPool.create(TcpConnectionPoolSettings(connection.localAddress, 2, nOfConnection, 5 seconds))
        connectionPool.startConnection()

        val clientSink = Sink.actorSubscriber(TcpStreamActor.props(connectionPool, 2))

        val probe = TestSource.probe[Send].toMat(clientSink)(Keep.left).run()

        val (connectMsg, registerMsg) = serverProbe.receiveN(nOfConnection * 2).partition {
          case _: Connected => true
          case _: Register => false
        }.asInstanceOf[(Seq[Connected], Seq[Register])]
        connectMsg.length must be (nOfConnection)
        registerMsg.length must be (nOfConnection)

        val handler = registerMsg.head.handler

        val responses1 = 1 to 20 map { _ =>
          val p = Promise[Unit]
          probe.sendNext(Send(ByteString("abcde"), p))
          Thread.sleep(5)
          p.future
        }

        handler ! Close

        val responses2 = 21 to 100 map { _ =>
          val p = Promise[Unit]
          probe.sendNext(Send(ByteString("abcde"), p))
          Thread.sleep(10)
          p.future
        }

        Await.result(Future.sequence(responses1 ++ responses2), 6 seconds).length must be (100)

        serverActor ! PoisonPill
      }
    }
  }
}
