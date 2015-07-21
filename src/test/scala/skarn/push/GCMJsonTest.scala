package skarn.push

import skarn.push.PushRequestHandleActorProtocol.Ex
import org.scalatest.{MustMatchers, WordSpecLike}

/**
 * Created by yusuke on 15/07/10.
 */
class GCMJsonTest extends  WordSpecLike
with MustMatchers {

  "GCMentity" must {
    "JSONシリアライズできる" in {
      import spray.json._
      import GCMProtocol._
      import GCMJsonProtocol._

      GCMEntity(Vector("deviceToken"), None, data = Some(List(Ex("message", """{"message_text":"message"}""")))).toJson.prettyPrint must be(
        """|{
          |  "registration_ids": ["deviceToken"],
          |  "data": {
          |    "message": "{\"message_text\":\"message\"}"
          |  }
          |}""".stripMargin)
    }

  }

  "List[Ex]" must {
    import spray.json._
    import PushRequestHandleActorJsonFormat._
    import PushRequestHandleActorProtocol._
    "serialize to single object" in {
      List(Ex("a", "b"), Ex("c", "d"), Ex("e", "f")).toJson.prettyPrint must be(
        """|{
          |  "e": "f",
          |  "c": "d",
          |  "a": "b"
          |}""".stripMargin)
    }
    "parse array of object with `key` and `value` fields" in {
      """
        |[
        |  {"key": "e", "value": "f"},
        |  {"key": "c", "value": "d"},
        |  {"key": "a", "value": "b"}
        |]
      """.stripMargin.parseJson.convertTo[List[Ex]] must be(List(Ex("e", "f"), Ex("c", "d"), Ex("a", "b")))
    }
  }
}
