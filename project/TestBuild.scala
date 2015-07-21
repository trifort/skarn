import sbt._
import Keys._

object TestBuild extends Build {
  lazy val E2ETest = config("e2e") extend(Test)
  lazy val ITest = config("it") extend(Test)
  def itFilter(name: String): Boolean = name endsWith "ITest"
  def unitFilter(name: String): Boolean = !itFilter(name) && !e2eFilter(name)
  def e2eFilter(name: String): Boolean = (name endsWith "E2ETest")

  override lazy val settings = super.settings ++ Seq(
    testOptions in Test := Seq(Tests.Filter(unitFilter)),
    testOptions in ITest := Seq(Tests.Filter(itFilter)),
    testOptions in E2ETest := Seq(Tests.Filter(e2eFilter)),
    parallelExecution in ITest := false
  )

}