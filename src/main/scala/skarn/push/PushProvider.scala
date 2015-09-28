package skarn
package push

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import akka.actor.ActorSystem
import akka.stream.io._
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.{BidiShape, ActorMaterializer}
import akka.util.ByteString
import skarn.apns.ApnsPushStreamProvider
import spray.json._
import scala.concurrent.{Promise, Future, ExecutionContext}
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.{PredefinedFromEntityUnmarshallers, Unmarshal}
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


trait IosPushStreamProvider extends ApnsPushStreamProvider with ServiceBaseContext {

  val service: ApnsService

  val requestUrl = "gateway.push.apple.com"

  val port = 2195

  implicit lazy val materializer = ActorMaterializer()


  /* @TanUkkii007 FIXME: This implementation has following problems.
   *  + APNS disconnects if token is malformed. Stream is cancelled when connection is closed.
   *  + APNS does not respond when request succeeds. More low level Ack based implementation is needed.
   *
   * Todo: Extract APNS client as a different library or package.
   */
  def send(deviceTokens: Vector[String], title: Option[String], body: Option[String], badge: Option[Int] = None, sound: Option[String] = None): Future[Seq[Unit]] = {
    import Apns._
    import APNSProtocol._
    import APNSJsonProtocol._
    val payload = APNSEntity(Notification(Alert(title, body), badge, sound)).toJson.compactPrint
    val data = deviceTokens.zipWithIndex.collect {
      case (token, i) => FrameData(Seq(DeviceToken(token), Payload(payload), Identifier(i))).serialize
    }
    send(data)(materializer)
  }
}

trait ApnsService extends SSLContextFactory with KeyManagerAlgorithm {
  val certificate: InputStream
  val password: String
  lazy val sslContext = createSSLContext(certificate, password, "PKCS12", algorithm)
}

trait SSLContextFactory {
  def createSSLContext(cert: InputStream, password: String, ksType: String, ksAlgorithm: String): SSLContext = {
    val ks = KeyStore.getInstance(ksType)
    ks.load(cert, password.toCharArray)
    val kmf = KeyManagerFactory.getInstance(ksAlgorithm)
    kmf.init(ks, password.toCharArray)

    val tmf = TrustManagerFactory.getInstance(ksAlgorithm)
    tmf.init(null.asInstanceOf[KeyStore])

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null)
    sslContext
  }
}

trait KeyManagerAlgorithm {
  lazy val algorithm = if (java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm") == null)
    "sunx509" else
    java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm")
}

trait ServiceBaseContext {
  implicit val system: ActorSystem
  implicit val executionContext: ExecutionContext
  val requestUrl: String
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

  def request(request: HttpRequest) = Source.single(request)
    .via(gcmConnectionFlow)
    .runWith(Sink.head)

  def send(deviceTokens: Vector[String], notification: Option[Notification], collapseKey: Option[String] = None, delayWhileIdle: Option[Boolean] = None, timeToLive: Option[Int] = None, data: Option[ExtraData] = None): Future[GCMResponse] = {
    import GCMProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import GCMJsonProtocol._
    import PredefinedFromEntityUnmarshallers._
    import HttpMethods._
    val entity = GCMEntity(deviceTokens, notification, collapse_key = collapseKey, delay_while_idle = delayWhileIdle, time_to_live = timeToLive, data = data)
    Marshal(entity).to[MessageEntity].flatMap { hRequest =>
      request(HttpRequest(method= POST, uri= requestPath, headers= headers, entity= hRequest)).flatMap { response =>
        /*
         * Todo: Use content negotiation
         */
        Unmarshal(response).to[GCMResponse].recoverWith {
          case e => {
            val p = Promise.apply()
            Unmarshal(response).to[String].foreach(s => p.failure(new DeserializationException(s)))
            p.future
          }
        }
      }
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

