package skarn
package routing

import spray.http.ContentRange
import spray.http.HttpHeaders.{`Content-Range`, Allow}
import spray.http.StatusCodes._
import spray.routing.AuthenticationFailedRejection.{CredentialsRejected, CredentialsMissing}
import spray.routing._
import spray.routing.directives.RouteDirectives._

/**
 * Created by yusuke on 2015/03/05.
 */
trait ErrorHandler {
  val rejectionHandler: RejectionHandler = PartialFunction.empty
}

/*
 * HTTP周辺の基本的なエラーをJSONで返すRejectionHandler
 * https://github.com/spray/spray/blob/release/1.3/spray-routing/src/main/scala/spray/routing/RejectionHandler.scala
 */
object BasicErrorHandler extends ErrorHandler {
  import ErrorResponseProtocol._
  override val rejectionHandler = RejectionHandler {
    case Nil ⇒ complete(NotFound, "The requested resource could not be found.")

    case AuthenticationFailedRejection(cause, challengeHeaders) :: _ ⇒
      val rejectionMessage = cause match {
        case CredentialsMissing  ⇒ "The resource requires authentication, which was not supplied with the request"
        case CredentialsRejected ⇒ "The supplied authentication is invalid"
      }
    { ctx ⇒ ctx.complete(Unauthorized, challengeHeaders, ErrorResponse(Unauthorized.intValue, rejectionMessage)) }

    case AuthorizationFailedRejection :: _ ⇒
      complete(Forbidden, ErrorResponse(Forbidden.intValue, "The supplied authentication is not authorized to access this resource"))

    case CorruptRequestEncodingRejection(msg) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "The requests encoding is corrupt:\n" + msg))

    case MalformedFormFieldRejection(name, msg, _) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "The form field '" + name + "' was malformed:\n" + msg))

    case MalformedHeaderRejection(headerName, msg, _) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, s"The value of HTTP header '$headerName' was malformed:\n" + msg))

    case MalformedQueryParamRejection(name, msg, _) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "The query parameter '" + name + "' was malformed:\n" + msg))

    case MalformedRequestContentRejection(msg, _) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "The request content was malformed:\n" + msg))

    case rejections @ (MethodRejection(_) :: _) ⇒
      val methods = rejections.collect { case MethodRejection(method) ⇒ method }
      complete(MethodNotAllowed, List(Allow(methods: _*)), ErrorResponse(MethodNotAllowed.intValue, "HTTP method not allowed, supported methods: " + methods.mkString(", ")))

    case rejections @ (SchemeRejection(_) :: _) ⇒
      val schemes = rejections.collect { case SchemeRejection(scheme) ⇒ scheme }
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "Uri scheme not allowed, supported schemes: " + schemes.mkString(", ")))

    case MissingCookieRejection(cookieName) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "Request is missing required cookie '" + cookieName + '\''))

    case MissingFormFieldRejection(fieldName) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "Request is missing required form field '" + fieldName + '\''))

    case MissingHeaderRejection(headerName) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "Request is missing required HTTP header '" + headerName + '\''))

    case MissingQueryParamRejection(paramName) :: _ ⇒
      complete(NotFound, ErrorResponse(NotFound.intValue, "Request is missing required query parameter '" + paramName + '\''))

    case RequestEntityExpectedRejection :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "Request entity expected but not supplied"))

    case TooManyRangesRejection(_) :: _ ⇒
      complete(RequestedRangeNotSatisfiable, ErrorResponse(RequestedRangeNotSatisfiable.intValue, "Request contains too many ranges."))

    case UnsatisfiableRangeRejection(unsatisfiableRanges, actualEntityLength) :: _ ⇒
      complete(RequestedRangeNotSatisfiable, List(`Content-Range`(ContentRange.Unsatisfiable(actualEntityLength))),
        ErrorResponse(RequestedRangeNotSatisfiable.intValue, unsatisfiableRanges.mkString("None of the following requested Ranges were satisfiable:\n", "\n", "")))

    case rejections @ (UnacceptedResponseContentTypeRejection(_) :: _) ⇒
      val supported = rejections.flatMap {
        case UnacceptedResponseContentTypeRejection(supported) ⇒ supported
        case _ ⇒ Nil
      }
      complete(NotAcceptable, ErrorResponse(NotAcceptable.intValue, "Resource representation is only available with these Content-Types:\n" + supported.map(_.value).mkString("\n")))

    case rejections @ (UnacceptedResponseEncodingRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnacceptedResponseEncodingRejection(supported) ⇒ supported }
      complete(NotAcceptable, ErrorResponse(NotAcceptable.intValue, "Resource representation is only available with these Content-Encodings:\n" + supported.map(_.value).mkString("\n")))

    case rejections @ (UnsupportedRequestContentTypeRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnsupportedRequestContentTypeRejection(supported) ⇒ supported }
      complete(UnsupportedMediaType, ErrorResponse(UnsupportedMediaType.intValue, "There was a problem with the requests Content-Type:\n" + supported.mkString(" or ")))

    case rejections @ (UnsupportedRequestEncodingRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnsupportedRequestEncodingRejection(supported) ⇒ supported }
      complete(BadRequest, ErrorResponse(BadRequest.intValue, "The requests Content-Encoding must be one the following:\n" + supported.map(_.value).mkString("\n")))

    case ValidationRejection(msg, _) :: _ ⇒
      complete(BadRequest, ErrorResponse(BadRequest.intValue, msg))
  }
}