import skarn.json._

import spray.revolver.RevolverPlugin._

name := "skarn-push-server"

scalaVersion := "2.11.7"

crossScalaVersions := Seq(scalaVersion.value)

organization := "jp.trifort"

versionWithGit

git.baseVersion := "0.2"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= {
  val akkaVersion       = "2.3.12"
  val sprayVersion      = "1.3.3"
  val kamonVersion      = "0.4.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"   % akkaVersion,
    "com.typesafe.akka" %%  "akka-multi-node-testkit"      % akkaVersion   % "test",
    "com.typesafe.akka" %%  "akka-testkit"                 % akkaVersion   % "test",
    "io.spray"          %% "spray-can"       % sprayVersion,
    "io.spray"          %% "spray-routing"   % sprayVersion,
    "io.spray"          %% "spray-client"   % sprayVersion,
    "io.spray"          %% "spray-json"      % "1.3.2",
    "io.spray" %% "spray-testkit" % sprayVersion % "test",
    "org.scalatest"     %% "scalatest"       % "2.2.4"       % "test",
    "io.kamon" %% "kamon-core" % kamonVersion,
    "io.kamon" %% "kamon-akka" % kamonVersion,
    "io.kamon" %% "kamon-spray" % kamonVersion,
    "io.kamon" %% "kamon-statsd" % kamonVersion,
    "io.kamon" %% "kamon-system-metrics" % kamonVersion,
    "io.kamon" % "sigar-loader" % "1.6.6",
    "org.aspectj" % "aspectjweaver" % "1.8.5",
    "com.notnoop.apns" % "apns" % "1.0.0.Beta6",
    "ch.qos.logback"  %  "logback-classic"    % "1.1.3"
  )
}

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:postfixOps", "-nobootcp")

mainClass in Compile := Some("skarn.BootApi")

javaOptions ++= Seq(
  "-Dhttp.port=8080",
  "-Djvm-debug 5005",
  "-Dkamon.statsd.hostname=172.17.8.101",
  s"-DCONFIG_PATH=${baseDirectory.value.getAbsolutePath}/service/service.conf",
  s"-DCERT_PATH=${baseDirectory.value.getAbsolutePath}/service/apns/apns.p12"
)

fork in run := true

mainClass in Revolver.reStart := Some("skarn.BootApi")

javaOptions in Revolver.reStart ++= Seq(
  "-Dhttp.port=8084",
  "-Djvm-debug 5005",
  "-Dkamon.statsd.hostname=172.17.8.101",
  s"-DCONFIG_PATH=${baseDirectory.value.getAbsolutePath}/service/service.conf",
  s"-DCERT_PATH=${baseDirectory.value.getAbsolutePath}/service/apns/apns.p12"
)

javaOptions in Revolver.reStart <++= AspectjKeys.weaverOptions in Aspectj

javaOptions <++= AspectjKeys.weaverOptions in Aspectj

AspectjKeys.aspectjVersion in Aspectj := "1.8.5"

Revolver.settings

genProjectInfo := {
  import JsonImplicitConversions._
  val resourceFile: File = (resourceManaged in Compile).value /  "project.json"
  val projectInfo = Map(
    JSString("name") -> name.value,
    JSString("version") -> version.value
  )
  IO.write(resourceFile, JSObject(projectInfo).toJson)
  Seq(resourceFile)
}

resourceGenerators in Compile += (genProjectInfo in Compile).taskValue

javaOptions in ITest ++= Seq(s"-DCONFIG_PATH=${baseDirectory.value.getAbsolutePath}/service/service.conf")

// for Kamon issue https://github.com/kamon-io/Kamon/issues/202

fork in (ITest, test) := true

fork in (ITest, testOnly) := true

fork in (ITest, testQuick) := true
