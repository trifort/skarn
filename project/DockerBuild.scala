import sbt._
import Keys._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging.autoImport._
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.archetypes._
import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.GitVersioning
import _root_.com.typesafe.sbt.GitPlugin.autoImport._

import com.typesafe.sbt.SbtAspectj.{ aspectjSettings }
import sbt.complete.Parser

object DockerBuild extends Build {

  lazy val root = (project in file(".")).settings((aspectjSettings ++ Defaults.coreDefaultSettings): _*).configs(TestBuild.E2ETest, TestBuild.ITest).settings(inConfig(TestBuild.E2ETest)(Defaults.testTasks) : _*).settings(inConfig(TestBuild.ITest)(Defaults.testTasks) : _*)


  lazy val api = ClusterProject("api").settings(
    mainClass in Compile := Some("skarn.BootApi")
  )

  lazy val genProjectInfo = taskKey[Seq[java.io.File]]("generate a resource file that contains project info")

  lazy val genDockerCompose = inputKey[Seq[java.io.File]]("generate a docker-compose file")

  def ClusterProject(projectName: String): Project = Project(projectName, file(projectName), settings = root.settings).dependsOn(root % "test->test;compile->compile").enablePlugins(JavaAppPackaging, DockerPlugin, GitVersioning).settings(
    scalaVersion := (scalaVersion in root).value,
    version := (version in root).value,
    fullClasspath := (fullClasspath in Compile).value,
    compile := (compile in Compile).value,
    NativePackagerKeys.maintainer in Docker := "Yusuke Yasuda <yyusuke@trifort.jp>",
    dockerBaseImage := "java",
    dockerRepository := Some(name in root value),
    dockerExposedPorts := Seq(8080, 8125),
    dockerEntrypoint := Seq("sh", "-c", "bin/" + name.value + " $*")
    //dockerUpdateLatest := true
  )

  override lazy val settings = super.settings ++ Seq(

      genDockerCompose := {
      val resourceFile: File = (target in Compile in root).value /  "docker-compose.yml"
      val versionHash = version.value
      val repo = (dockerRepository in api).value.get
      val configPath = stringParser.parsed

      val yml =
        s"""
          |api1:
          |  image: $repo/api:$versionHash
          |  environment:
          |  - "CONFIG_PATH=/tmp/service.json"
          |  volumes:
          |  - "$configPath:/tmp/service.json"
          |  ports:
          |  - "8081:8081"
    """.stripMargin

      IO.write(resourceFile, yml)
      Seq(resourceFile)
    }
  )

  val stringParser: Parser[String] = {
    import complete.DefaultParsers._
    Space.* ~> StringBasic
  }

}