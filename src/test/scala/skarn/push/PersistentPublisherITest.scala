package skarn.push

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Keep}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import persistence.Cleanup
import skarn.push.PersistentPublisher.Persist

/**
 * Created by yusuke on 15/09/01.
 */

class PersistentPublisherITest extends TestKit(ActorSystem("PersistentPublisherITest", ConfigFactory.parseString(
  """
    |akka.persistence.journal.leveldb.native=off
  """.stripMargin))
) with WordSpecLike with MustMatchers with Cleanup with ImplicitSender {

  "PersistentPublisher" must {
    implicit lazy val materializer = ActorMaterializer()
    "must read value from journal when requested" in {
      val persistentSource = Source.actorPublisher[Persistent](PersistentPublisher.props("PersistentPublisherITest"))
      val (ref, probe) = persistentSource.toMat(TestSink.probe[Persistent])(Keep.both).run()
      Thread.sleep(1000)
      1 to 200 map (P(_)) map (Persist(_)) foreach (ref ! _)
      Thread.sleep(1000)
      probe.request(1)
      probe.expectNext(P(1))
      probe.request(10)
      probe.expectNextN(2 to 11 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(12 to 21 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(22 to 31 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(32 to 41 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(42 to 51 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(52 to 61 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(62 to 71 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(72 to 81 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(82 to 91 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(92 to 101 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(102 to 111 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(112 to 121 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(122 to 131 map (P(_)))
      probe.request(10)
      Thread.sleep(100)
      probe.expectNextN(132 to 141 map (P(_)))
    }
  }
}
