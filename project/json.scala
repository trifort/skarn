package skarn
package json

trait JSValue {
  def toJson: String
}
case object JSNull extends JSValue {
  def toJson = "null"
}
case object JSTrue extends JSValue {
  def toJson = "true"
}
case object JSFalse extends JSValue {
  def toJson = "false"
}
case class JSInt(value: Int) extends JSValue {
  def toJson = value.toString
}
case class JSString(value: String) extends JSValue {
  def toJson = s""""$value""""
  def ->(v: JSValue) = (this, v)
}
case class JSArray(value: TraversableOnce[JSValue]) extends JSValue {
  def toJson = "[" + value.map(_.toJson).mkString(",") + "]"
}
case class JSObject(value: Map[JSString, JSValue]) extends JSValue {
  def toJson = "{" + value.map{ pair =>
    val (key, v) = pair
    s"${key.toJson}:${v.toJson}"
  }.mkString(",") + "}"
}

object JsonImplicitConversions {
  implicit def string(s: String) = new JSString(s)
  implicit def int(i: Int) = new JSInt(i)
  implicit def list(l: List[JSValue]) = new JSArray(l)
  implicit def map(m: Map[JSString, JSValue]) = new JSObject(m)
}