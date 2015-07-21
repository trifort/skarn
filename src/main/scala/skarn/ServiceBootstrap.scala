/**
 * Created by yusuke on 2015/02/09.
 */
package skarn

import akka.actor.ActorSystem
import com.typesafe.config.Config

trait ServiceBootstrap {
  val role: String
  val config: Config
  def run(implicit system: ActorSystem): Unit
}
