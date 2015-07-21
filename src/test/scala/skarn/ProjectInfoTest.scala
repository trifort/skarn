/**
 * Created by yusuke on 2015/02/08.
 */
package skarn
import org.scalatest.{MustMatchers, WordSpecLike}

class ProjectInfoTest  extends WordSpecLike with MustMatchers {
  "Version" must {
    "Version.applyはバージョン文字列をパースする（コミットSHA付き）" in {
      Version("0.1-8243e98bb04b0680cc406882f604d3c9c27428a9") must be(
        Version(0,1,Some("8243e98bb04b0680cc406882f604d3c9c27428a9"))
      )
    }
    "Version.applyはバージョン文字列をパースする（本番）" in {
      Version("1.0") must be(
        Version(1,0,None)
      )
    }
    "Version.applyはパースできなかった場合に例外を投げる" in {
      an [Exception] must be thrownBy Version("s1mxp1dxk-1w")
    }
    "Version.toStringはもとのバージョン文字列を返す" in {
      Version("0.1-8243e98bb04b0680cc406882f604d3c9c27428a9").toString must be(
        "0.1-8243e98bb04b0680cc406882f604d3c9c27428a9"
      )
      Version("1.0").toString must be(
        "1.0"
      )
    }
  }
}
