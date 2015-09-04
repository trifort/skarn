package skarn
package push

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import akka.stream.scaladsl.{Sink, Source, Keep}
import skarn.push.PushRequestHandleActorProtocol.PushEntity
import definition.Platform
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import scala.concurrent.duration._


/**
 * Created by yusuke on 15/08/12.
 */
class PushRequestQueueTest extends TestKit(ActorSystem({"PushRequestQueueTest"}, ConfigFactory.parseString(
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
  """.stripMargin)
)) with WordSpecLike with MustMatchers with ImplicitSender { testSelf =>
  implicit lazy val materializer = ActorMaterializer()

  class TestPushRequestQueue(maxRetry: Short, pushActorRef: ActorRef) extends PushRequestQueue(maxRetry, pushActorRef, 1000) {
    import PushRequestQueue._
    import akka.stream.actor.ActorSubscriberMessage._
    val testEntity = PushEntity(Vector("deviceToken"), Platform.Ios, Some("message"), None)

    buf = Buffer.empty.copy(processing = (1 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap)

    def testReceive: Receive = {
      case OnNext(m) => {
        self forward m
        pushActorRef ! m
      }
    }

    override def receive = testReceive orElse(super.receive)
  }

  object TestPushRequestQueue {
    def props(maxRetry: Short, pushActorRef: ActorRef) = Props(new TestPushRequestQueue(maxRetry, pushActorRef))
  }

  "PushRequestQueue" must {
    import PushRequestQueue._
    val testEntity = PushEntity(Vector("deviceToken"), Platform.Ios, Some("message"), None)

    "act as Subscriber so that it receives Done and remove finished messages from processing buffer" in {
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      val requests = 1 to 4 map(id => Append(QueueRequest(id, testEntity)))

      requests.foreach(ref ! _)

      receiveN(4) must be(Seq.fill(4)(Accepted))

      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 4).map(QueueRequest(_, testEntity)).toVector))


      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(1))
      expectMsg(Done(1))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(2))
      expectMsg(Done(2))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((3 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(3))
      expectMsg(Done(3))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((4 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Done(4))
      expectMsg(Done(4))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map.empty))
    }

    "act as Subscriber so that it receives Retry to retry failed messages" in {
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(3, testActor))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()


      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Retry(1))
      expectMsg(Retry(1))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 1).map(QueueRequest(_, testEntity, retry= 1)).toVector))

      probe.sendNext(Retry(2))
      expectMsg(Retry(2))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((3 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer((1 to 2).map(QueueRequest(_, testEntity, retry= 1)).toVector))


      probe.sendNext(Retry(3))
      expectMsg(Retry(3))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((4 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))
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
      val pushSink = Sink.actorSubscriber[Command](TestPushRequestQueue.props(0, testActor))

      val (probe, ref) = TestSource.probe[Command]
        .toMat(pushSink)(Keep.both).run()

      ref ! GetProcessing()
      expectMsg(CurrentProcessing((1 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))

      probe.sendNext(Retry(1))
      expectMsg(Retry(1))
      expectNoMsg(2 seconds) // wait until following Done message is processed
      ref ! GetProcessing()
      expectMsg(CurrentProcessing((2 to 4).map(id => (id, QueueRequest(id, testEntity))).toMap))
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector.empty))

    }

    "act as Publisher to send messages from buffer" in {
      val pushSource = Source.actorPublisher[QueueRequest](PushRequestQueue.props(3, testActor))
      val req1 = QueueRequest(1, testEntity)
      val req2 = QueueRequest(2, testEntity)

      val (ref, probe) = pushSource.toMat(TestSink.probe[QueueRequest])(Keep.both).run()
      ref ! Append(req1)
      expectMsg(Accepted)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req1)))
      ref ! Append(req2)
      expectMsg(Accepted)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req1, req2)))

      probe.request(1)
      probe.expectNext(req1)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector(req2)))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map(1 -> req1)))

      probe.request(1)
      probe.expectNext(req2)
      ref ! GetBuffer()
      expectMsg(CurrentBuffer(Vector.empty))
      ref ! GetProcessing()
      expectMsg(CurrentProcessing(Map(1 -> req1, 2 -> req2)))
    }
  }
}
