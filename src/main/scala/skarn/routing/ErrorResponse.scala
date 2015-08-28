package skarn
package routing

/**
 * Created by yusuke on 2015/03/05.
 */

import spray.http.{StatusCodes, StatusCode}
import spray.json.DefaultJsonProtocol

/*
 * エラーレスポンスの統一されたJSONの形式
 */
case class ErrorResponse(errors: List[ErrorDetail])

object ErrorResponse {
  def apply(code: Int, message: String, url: String = ""): ErrorResponse = ErrorResponse(List(ErrorDetail(code, message, url)))
}

case class ErrorDetail(code: Int, message: String, documentationUrl: String = "")

object ErrorResponseProtocol extends DefaultJsonProtocol {
  import spray.httpx.SprayJsonSupport._
  implicit val ErrorDetailFormat = jsonFormat3(ErrorDetail)
  implicit val ErrorResponseFormat = jsonFormat1[List[ErrorDetail], ErrorResponse](ErrorResponse.apply)
  implicit val ErrorResponseUnmarshaller = sprayJsonUnmarshallerConverter(ErrorResponseFormat)
  implicit val ErrorResponseMarshaller = sprayJsonMarshallerConverter(ErrorResponseFormat)
}

case class ErrorFormat(response: ErrorResponse, status: StatusCode)

object ErrorFormat {
  import StatusCodes._
  //タイムアウト
  val TIMEOUT = ErrorFormat(ErrorResponse(500, "The server was not able to produce a timely response to your request."), InternalServerError)
  //PUSH
  val INVALID_PUSH_ACCESS_TOKEN = ErrorFormat(ErrorResponse(1001, "Push access token is invalid."), Unauthorized)
  val NO_ACCESS_TOKEN = ErrorFormat(ErrorResponse(1002, "Push access token cannot be found in X-AUTH-TOKEN header."), Unauthorized)
  val BUFFER_OVERFLOW = ErrorFormat(ErrorResponse(1003, "Cannot accept push requests because buffer is full."), Unauthorized)
}