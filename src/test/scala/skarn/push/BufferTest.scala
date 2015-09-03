package skarn.push
import skarn.definition._
import org.scalatest.{MustMatchers, WordSpecLike}
import skarn.push.PushRequestHandleActorProtocol.PushEntity
import skarn.push.PushRequestQueue.QueueRequest

/**
 * Created by yusuke on 15/09/03.
 */
class BufferTest extends WordSpecLike with MustMatchers {
  "Buffer" must {
    val entity = PushEntity(Vector("tokens"), Platform.Ios, None, None)

    "append" in {
      val buffer = Buffer.empty
      buffer.append(QueueRequest(1, entity)) must be(Buffer(Vector(QueueRequest(1, entity)), Map.empty[Int, QueueRequest]))
      buffer
        .append(QueueRequest(1, entity))
        .append(QueueRequest(2, entity)) must be(
        Buffer(Vector(QueueRequest(1, entity), QueueRequest(2, entity)), Map.empty[Int, QueueRequest])
      )
    }

    "concat" in {
      val buffer = Buffer.empty
      buffer.concat(Array(QueueRequest(1, entity), QueueRequest(2, entity))) must be(
        Buffer(Vector(QueueRequest(1, entity), QueueRequest(2, entity)), Map.empty[Int, QueueRequest])
      )
    }

    "process" in {
      val buffer = Buffer.empty.concat(Array(QueueRequest(1, entity), QueueRequest(2, entity)))
      buffer.process(1)._1 must be (Buffer(Vector(QueueRequest(2, entity)), Map(1 -> QueueRequest(1, entity))))
      buffer.process(1)._2 must be(Vector(QueueRequest(1, entity)))
    }

    "immediatelyProcess" in {
      val buffer = Buffer.empty.append(QueueRequest(1, entity))
      buffer.immediatelyProcess(QueueRequest(2, entity)) must be(Buffer(Vector(QueueRequest(1, entity)), Map(2 -> QueueRequest(2, entity))))
    }

    "doneWith" in {
      val buffer = Buffer.empty.concat(Array(QueueRequest(1, entity), QueueRequest(2, entity))).process(1)._1
      buffer.doneWith(1) must be(Buffer(Vector(QueueRequest(2, entity)), Map.empty))
    }

    "retry" in {
      val buffer = Buffer.empty.concat(Array(QueueRequest(1, entity), QueueRequest(2, entity))).process(1)._1
      buffer.retry(1) must be(Buffer(Vector(QueueRequest(2, entity), QueueRequest(1, entity, retry= 1)), Map.empty[Int, QueueRequest]))
    }
  }
}
