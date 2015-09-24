package skarn.apns

import javax.net.ssl.SSLContext

import akka.stream.BidiShape
import akka.stream.io._
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.util.ByteString
import skarn.push.ApnsService

/**
 * Created by yusuke on 15/09/18.
 */
trait TlsFlow {
  val sslContext: SSLContext

  val parser = BidiFlow() { implicit b =>
    val wrapTls = b.add(Flow[ByteString].map(SendBytes(_)))
    val unwrapTls = b.add(Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => bytes })
    BidiShape(wrapTls, unwrapTls)
  }

  lazy val tlsFlow = parser.atop(SslTls(sslContext, NegotiateNewSession, Role.client))
}
