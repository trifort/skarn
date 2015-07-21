package skarn.definition

import spray.json._

/**
 * Created by yusuke on 2015/04/08.
 */

//1: IOS, 2: Android
object Platform {
  case object Ios extends Platform {
    val id: Byte = 1
  }
  case object Android extends Platform {
    val id: Byte = 2
  }
  case object Unknown extends Platform {
    val id: Byte = 0
  }
  def apply(platformId: Int) = {
    platformId match {
      case Ios.id => Ios
      case Android.id => Android
      case _ => Unknown
    }
  }
}

sealed trait Platform {
  val id: Byte
}

object PlatformJsonProtocol extends DefaultJsonProtocol {
  implicit object PlatformJsonFormat extends RootJsonFormat[Platform] {
    def write(platform: Platform) = JsNumber(platform.id)
    def read(value: JsValue) = value match {
      case JsNumber(number) => Platform(number.toInt)
      case x => deserializationError("Expected Platform as JsNumber, but got " + x)
    }
  }
}