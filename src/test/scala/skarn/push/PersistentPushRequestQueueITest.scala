package skarn.push

import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Keep, Sink}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import persistence.Cleanup
import skarn.definition.Platform
import skarn.push.PushRequestHandleActorProtocol.PushEntity
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * Created by yusuke on 15/09/03.
 */

class WatchActor(target: ActorRef, reportTo: ActorRef) extends Actor with ActorLogging {
  context.watch(target)
  def receive = {
    case msg: Terminated => {
      log.info("terminated: {}", msg.actor)
      reportTo forward "terminated"
    }
  }
}

object WatchActor {
  def props(target: ActorRef, reportTo: ActorRef) = Props(new WatchActor(target, reportTo))
}

class PersistentPushRequestQueueITest extends TestKit(ActorSystem({"PersistentPushRequestQueueITest"}, ConfigFactory.parseString(
  """
    |push-request-queue-dispatcher {
    |  mailbox-type = "skarn.push.PushRequestQueueBoundedPriorityMailbox"
    |  mailbox-capacity = 11
    |  fork-join-executor {
    |    parallelism-min = 1
    |    parallelism-factor = 1.0
    |    parallelism-max = 1
    |  }
    |}
    |akka.persistence.journal.leveldb.native=off
    |akka {
    |  loglevel = "INFO"
    |  actor {
    |    debug {
    |      receive = on
    |      autoreceive = on
    |      lifecycle = on
    |    }
    |  }
    |}
  """.stripMargin)
)) with WordSpecLike with MustMatchers with ImplicitSender with Cleanup { testSelf =>
  implicit lazy val materializer = ActorMaterializer()

  class TestPushRequestQueue(maxRetry: Short, pushActorRef: ActorRef, persistenceId: String) extends PersistentPushRequestQueue(maxRetry, pushActorRef, 1000, persistenceId) {
    import PushRequestQueue._
    import akka.stream.actor.ActorSubscriberMessage._
    val testEntity = PushEntity(Vector("deviceToken"), Platform.Ios, Some("message"), None)

    buf = Buffer.empty.copy(processing = (1L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap)

    override def persisting: Receive = {
      val receive: Receive = {
        case OnNext(m) => {
          self forward m
          pushActorRef ! m
        }
      }
      receive orElse super.persisting
    }
  }

  object TestPushRequestQueue {
    def props(maxRetry: Short, pushActorRef: ActorRef, persistenceId: String) = Props(new TestPushRequestQueue(maxRetry, pushActorRef, persistenceId))
  }

  "PersistentPushRequestQueue" must {
    import PushRequestQueue._
    import PersistentPushRequestQueueProtocol._

    implicit val timeout = akka.util.Timeout(5 seconds)
    val testEntity = PushEntity(Vector("deviceToken"), Platform.Ios, Some("message"), None)

    "act as Subscriber so that it receives Done and remove finished messages from processing buffer" in {
      import akka.pattern.ask
      import system.dispatcher
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor, "pushSink-1"))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      val requests = 1 to 4 map(id => AppendEvt(QueueRequest(id, testEntity)))

      requests.foreach(ref ! _)

      receiveN(4) must be(Seq.fill(4)(Accepted))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))


      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(1))
      expectMsg(Done(1))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(2))
      expectMsg(Done(2))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((3L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(3))
      expectMsg(Done(3))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((4L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(4))
      expectMsg(Done(4))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map.empty))
    }

    "act as Subscriber so that it receives Retry to retry failed messages" in {
      import akka.pattern.ask
      import system.dispatcher
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor, "pushSink-2"))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Retry(1))
      expectMsg(Retry(1))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 1).map(QueueRequest(_, testEntity, retry= 1)).toVector))

      probe.sendNext(Retry(2))
      expectMsg(Retry(2))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((3L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 2).map(QueueRequest(_, testEntity, retry= 1)).toVector))


      probe.sendNext(Retry(3))
      expectMsg(Retry(3))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((4L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 3).map(QueueRequest(_, testEntity, retry= 1)).toVector))


      probe.sendNext(Retry(4))
      expectMsg(Retry(4))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map.empty))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity, retry= 1)).toVector))
    }

    "act as Subscriber so that it aborts to retry when retry limit exceeds" in {
      import akka.pattern.ask
      import system.dispatcher
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(0, testActor, "pushSink-3"))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Retry(1))
      expectMsg(Retry(1))
      expectNoMsg(2 seconds) // wait until following Done message is processed
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector.empty))

    }

    "act as Publisher to send messages from buffer" in {
      import akka.pattern.ask
      import system.dispatcher

      val pushSource = Source.actorPublisher[QueueRequest](PersistentPushRequestQueue.props(3, testActor, 1000, "pushSource-1"))
      val req1 = QueueRequest(1, testEntity)
      val req2 = QueueRequest(2, testEntity)

      val (ref, probe) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref ! AppendEvt(req1)
      expectMsg(Accepted)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req1)))
      ref ! AppendEvt(req2)
      expectMsg(Accepted)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req1, req2)))

      probe.request(1)
      probe.expectNext(req1)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req2)))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map(1L -> req1)))

      probe.request(1)
      probe.expectNext(req2)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector.empty))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map(1L -> req1, 2L -> req2)))
    }

    "recover buffer at startup" in {
      import akka.pattern.ask
      import system.dispatcher

      val pushSource = Source.actorPublisher[QueueRequest](PersistentPushRequestQueue.props(3, testActor, 1000, "pushSource-2"))
      val (ref, _) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()

      val watchActor = system.actorOf(WatchActor.props(ref, testActor))

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      val requests = 1 to 4 map(id => AppendEvt(QueueRequest(id, testEntity)))
      requests.foreach(ref ! _)

      receiveN(4) must be(Seq.fill(4)(Accepted))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))

      ref ! PoisonPill
      expectMsg("terminated")

      val pushSource2 = Source.actorPublisher[QueueRequest](PersistentPushRequestQueue.props(3, testActor, 1000, "pushSource-2"))
      val (ref2, probe2) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()

      awaitCond(Await.result(ref2 ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref2 ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))

    }

    "recover all messages that are not `Done`" in {
      import akka.pattern.ask
      import system.dispatcher

      val pushSource = Source.actorPublisher[QueueRequest](PersistentPushRequestQueue.props(3, testActor, 1000, "pushSource-3"))
      val (ref, probe) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()

      val watchActor = system.actorOf(WatchActor.props(ref, testActor))

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      val requests = 1 to 4 map(id => AppendEvt(QueueRequest(id, testEntity)))
      requests.foreach(ref ! _)

      receiveN(4) must be(Seq.fill(4)(Accepted))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))

      probe.request(1)
      probe.expectNext(QueueRequest(1, testEntity))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((2 to 4).map(QueueRequest(_, testEntity)).toVector))

      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map(1L -> QueueRequest(1, testEntity))))

      ref ! PoisonPill
      expectMsg("terminated")

      val pushSource2 = Source.actorPublisher[QueueRequest](PersistentPushRequestQueue.props(3, testActor, 1000, "pushSource-3"))
      val (ref2, probe2) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()

      awaitCond(Await.result(ref2 ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref2 ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))
    }

    "recover buffer that does not contain `Done` messages" in {
      import akka.pattern.ask
      import system.dispatcher
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor, "pushSink-4"))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      val watchActor = system.actorOf(WatchActor.props(ref, testActor))

      awaitCond(Await.result(ref ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      val requests = 1 to 4 map(id => AppendEvt(QueueRequest(id, testEntity)))

      requests.foreach(ref ! _)

      receiveN(4) must be(Seq.fill(4)(Accepted))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))


      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(1))
      expectMsg(Done(1))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(2))
      expectMsg(Done(2))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((3L to 4L).map(id => (id, QueueRequest(id, testEntity))).toMap))

      ref ! PoisonPill
      expectMsg("terminated")

      val pushSink2 = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor, "pushSink-4"))

      val (probe2, ref2) = TestSource.probe[Command]
        .toMat(pushSink2)(Keep.both).run()

      awaitCond(Await.result(ref2 ? CurrentState, 3 seconds) == Recovered, 10 seconds)

      ref2 ! GetBuffer()
      expectMsg(CurrentBuffer((3 to 4).map(QueueRequest(_, testEntity)).toVector))
    }
  }
}
