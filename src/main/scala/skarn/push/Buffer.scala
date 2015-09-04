package skarn.push

import skarn.push.PushRequestQueue.QueueRequest

/**
 * Created by yusuke on 15/09/03.
 */

case class Buffer(buffer: Vector[QueueRequest], processing: Map[Long, QueueRequest]) {
  def append(elem: QueueRequest) = copy(buffer= buffer :+ elem)
  def concat(elems: Array[QueueRequest]) = copy(buffer= buffer ++ elems)
  def doneWith(id: Long) = copy(processing= processing - id)
  def process(num: Int) = {
    val (use, keep) = buffer.splitAt(num)
    (copy(keep, processing ++ use.map(m => (m.id, m))), use)
  }
  def retry(id: Long) = {
    processing.get(id) match {
      case Some(message) => {
        doneWith(id).append(message.copy(retry = (message.retry + 1).toShort))
      }
      case None => this
    }
  }
  def immediatelyProcess(elem: QueueRequest) = copy(processing = processing + (elem.id -> elem))
  def total = buffer.size + processing.size
}

object Buffer {
  def empty = Buffer(Vector.empty, Map.empty)
}
