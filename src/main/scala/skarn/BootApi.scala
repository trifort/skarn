/**
 * Created by yusuke on 2015/01/12.
 */

package skarn

object BootApi extends App with Bootable {
  val serviceBootstrap = Api

  startup()
}
