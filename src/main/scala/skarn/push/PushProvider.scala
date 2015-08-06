package skarn
package push

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.notnoop.apns.{ApnsService, APNS}
import com.notnoop.apns.internal.Utilities
import spray.client.pipelining._
import spray.httpx.{SprayJsonSupport}
import spray.json._
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.JavaConversions._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers._
import scala.collection.immutable

/**
 * Created by yusuke on 2015/04/27.
 */

object APNSJsonProtocol extends DefaultJsonProtocol {
  import APNSProtocol._
  implicit val AlertFormat = jsonFormat2(Alert)
  implicit val NotificationFormat = jsonFormat3(Notification)
  implicit val APNSEntityFormat = jsonFormat1(APNSEntity)
}

object GCMJsonProtocol extends DefaultJsonProtocol {
  import PushRequestHandleActorJsonFormat._
  import GCMProtocol._
  implicit val NotificationFormat = jsonFormat6(Notification)
  implicit val GCMEntityFormat = jsonFormat6(GCMEntity)
  implicit val GCMResultFormat = jsonFormat3(GCMResult)
  implicit val GCMResponseFormat = jsonFormat5(GCMResponse)
}

object APNSProtocol {
  case class Alert(title: Option[String], body: Option[String])
  case class Notification(alert: Alert, badge: Option[Int], sound: Option[String])
  case class APNSEntity(aps: Notification)
}

trait IosPushProvider {

  def send(deviceTokens: Vector[String], title: Option[String], body: Option[String], badge: Option[Int] = None, sound: Option[String] = None)(implicit service: ApnsService) = {
    import APNSProtocol._
    import APNSJsonProtocol._
    val payload = APNSEntity(Notification(Alert(title, body), badge, sound)).toJson.compactPrint
    service.push(deviceTokens.toIterable, payload)
  }
}

trait IosProductionPushService {
  val certificate: InputStream
  val password: String

  def service = APNS.newService()
    .withSSLContext(Utilities.newSSLContext(certificate, password, "PKCS12", algorithm))
    .withProductionDestination()
    .build()

  protected lazy val algorithm = if (java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm") == null)
    "sunx509" else
    java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm")
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

trait AndroidPushStreamProvider extends ServiceBaseContext {
  import PushRequestHandleActorProtocol.ExtraData
  import GCMProtocol._

  val requestUrl = "gcm-http.googleapis.com"
  val requestPath = "/gcm/send"
  val apiKey: String

  implicit lazy val materializer = ActorMaterializer()

  lazy val gcmConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
    Http()(system).outgoingConnectionTls(requestUrl)
  }

  def request(request: HttpRequest) = Source.single(request).via(gcmConnectionFlow).runWith(Sink.head)

  def send(deviceTokens: Vector[String], notification: Option[Notification], collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, data: Option[ExtraData] = None): Future[GCMResponse] = {
    import GCMProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import GCMJsonProtocol._
    import HttpMethods._
    val entity = GCMEntity(deviceTokens, notification, collapse_key = collapseKey, delay_while_idle = delayWhileIdle, time_to_live = timeToLive, data = data)
    Marshal(entity).to[MessageEntity].flatMap { hRequest =>
      request(HttpRequest(method= POST, uri= requestPath, headers= headers, entity= hRequest)).flatMap(Unmarshal(_).to[GCMResponse])
    }
  }

  case class Authorization(value: String) extends CustomHeader {
    val name = "Authorization"
  }

  private[this] lazy val headers = {
    import MediaTypes._
    immutable.Seq(Accept(`application/json`), Authorization(s"key=$apiKey"))
  }
}

object GCMProtocol {
  import PushRequestHandleActorProtocol.ExtraData
  case class Notification(title: Option[String], body: Option[String] = None, icon: Option[String] = None, sound: Option[String] = None, tag: Option[String] = None, color: Option[String] = None)
  case class GCMEntity(registration_ids: Vector[String], notification: Option[Notification], collapse_key: Option[String] = None, delay_while_idle: Option[Boolean] = None, time_to_live: Option[Int] = None, data: Option[ExtraData] = None)
  case class GCMResponse(multicast_id: Long, success: Int, failure: Int, canonical_ids: Int, results: List[GCMResult])
  case class GCMResult(message_id: String, registration_id: Option[String], error: Option[String])
}

