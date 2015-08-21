package skarn.push

import org.scalatest.{MustMatchers, WordSpecLike}
/**
 * Created by yusuke on 15/08/21.
 */
class ApnsTest extends WordSpecLike with MustMatchers {
  "Apns.decodeHex" must {
    "decode hex string" in {
      val token = "56f9c4708cd395a968c3802871ffc090d0e095f86a8f2b93ab111d992c9aab24"
      val result = Array(86,-7,-60,112,-116,-45,-107,-87,104,-61,-128,40,113,-1,-64,-112,-48,-32,-107,-8,106,-113,43,-109,-85,17,29,-103,44,-102,-85,36)
      Apns.decodeHex(token) must be (result)
    }
  }
}
