import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      version := "0.1.0-SNAPSHOT",
      scalaVersion := "2.12.3",
      scalacOptions ++= Seq(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xfatal-warnings",
        "-Xlint",
        "-Xlint:-unused,_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-nullary-override",
        "-Ywarn-numeric-widen"
      )
    )),
    name := "Hello",
    libraryDependencies ++= Seq(
      akkaStream,
      akkaHttp,
      akkaSlf4j,
      logback,
      scalaTest % Test
    )
  )
