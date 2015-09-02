package persistence

import akka.testkit.TestKit
import org.apache.commons.io.FileUtils
import org.scalatest.{Suite}
import skarn.StopSystemAfterAllWithAwaitTermination

/**
 * Created by yusuke on 15/08/31.
 */
trait Cleanup extends StopSystemAfterAllWithAwaitTermination { this: TestKit with Suite =>

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s ⇒ new java.io.File(system.settings.config.getString(s)))

  override def beforeAll = {
    super.beforeAll
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  override def afterAll = {
    super.afterAll
    storageLocations.foreach(FileUtils.deleteDirectory)
  }
}
