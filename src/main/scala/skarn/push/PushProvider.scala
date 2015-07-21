package skarn
package push

import java.io.InputStream

import akka.actor.ActorSystem
import com.notnoop.apns.{ApnsService, APNS}
import spray.client.pipelining._
import spray.httpx.{SprayJsonSupport}
import spray.json._
import scala.concurrent.ExecutionContext
import scala.collection.JavaConversions._

/**
 * Created by yusuke on 2015/04/27.
 */

object GCMJsonProtocol extends DefaultJsonProtocol {
  import PushRequestHandleActorJsonFormat._
  import GCMProtocol._
  implicit val NotificationFormat = jsonFormat6(Notification)
  implicit val GCMEntityFormat = jsonFormat6(GCMEntity)
  implicit val GCMResultFormat = jsonFormat3(GCMResult)
  implicit val GCMResponseFormat = jsonFormat5(GCMResponse)
}

trait PushProvider {

}

trait IosPushProvider {

  def send(deviceTokens: Vector[String], message: String, badge: Option[Int] = None, sound: Option[String] = None)(implicit service: ApnsService) = {
    val payload = APNS.newPayload().alertBody(message)
    badge match {
      case Some(num) => payload.badge(num)
      case None => payload
    }
    sound match {
      case Some(soundType) => payload.sound(soundType)
      case None => payload
    }
    service.push(deviceTokens.toIterable, payload.build())
  }
}

trait IosProductionPushService {
  val certificate: InputStream
  val password: String
  def service = APNS.newService()
    .withCert(certificate, password)
    .withProductionDestination()
    .build()
}

trait ServiceBaseContext {
  implicit val system: ActorSystem
  implicit val executionContext: ExecutionContext
  val requestUrl: String
}

trait AndroidPushProvider extends ServiceBaseContext {
  import PushRequestHandleActorProtocol.ExtraData
  import GCMProtocol._
  val requestUrl = "https://gcm-http.googleapis.com/gcm/send"

  val apiKey: String

  lazy val pipeline = {
    import SprayJsonSupport._
    import GCMJsonProtocol._
    addHeader("Authorization", s"key=$apiKey") ~> sendReceive(system, executionContext) ~> unmarshal[GCMResponse]
  }

  def send(deviceTokens: Vector[String], notification: Option[Notification], collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, data: Option[ExtraData] = None) = {
    import GCMProtocol._
    import SprayJsonSupport._
    import GCMJsonProtocol._
    pipeline {
      Post(requestUrl, GCMEntity(deviceTokens, notification, collapse_key = collapseKey, delay_while_idle = delayWhileIdle, time_to_live = timeToLive, data = data))
    }
  }
}

object GCMProtocol {
  import PushRequestHandleActorProtocol.ExtraData
  case class Notification(title: String, body: Option[String] = None, icon: Option[String] = None, sound: Option[String] = None, tag: Option[String] = None, color: Option[String] = None)
  case class GCMEntity(registration_ids: Vector[String], notification: Option[Notification], collapse_key: Option[String] = None, delay_while_idle: Option[Boolean] = None, time_to_live: Option[Int] = None, data: Option[ExtraData] = None)
  case class GCMResponse(multicast_id: Long, success: Int, failure: Int, canonical_ids: Int, results: List[GCMResult])
  case class GCMResult(message_id: String, registration_id: Option[String], error: Option[String])
}

