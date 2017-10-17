import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.6"
  lazy val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.5.6"
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.10"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
}
