import com.trueaccord.scalapb.{ScalaPbPlugin => PB}
import de.heikoseeberger.sbtheader.license.MIT
import sbt.Keys._
import sbt._

val sOptions = Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

val jOptions = Seq(
  "-source",
  "1.8",
  "-target",
  "1.8",
  "-Xlint:unchecked",
  "-Xlint:deprecation",
  "-Xlint:-options"
)

lazy val protobufSettings = PB.protobufSettings ++ Seq(
  version in PB.protobufConfig := "3.0.0-beta-3",
  // Protoc from jar
  PB.runProtoc in PB.protobufConfig := (args =>
    com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray))
)


lazy val kafkaWire = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(protobufSettings: _*)
    .settings(
      name := "kafka-wire",
      version := "0.1.0",
      scalaVersion := "2.11.8",
      scalacOptions ++= sOptions,
      javacOptions ++= jOptions,
      headers := Map(
        "scala" -> MIT("2016", "Cake Solutions Limited"),
        "conf" -> MIT("2016", "Cake Solutions Limited", "#")
      ),
      libraryDependencies ++= Seq(
        "com.lihaoyi" %% "autowire" % "0.2.5",
        "net.cakesolutions" %% "scala-kafka-client-akka" % "0.10.0.0-RC1",
        "net.cakesolutions" %% "scala-kafka-client-testkit" % "0.10.0.0-RC1" % Test,
        "com.typesafe.akka" %% "akka-testkit" % "2.4.8" % Test,
        "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.5.32",
        "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % "0.5.32",
        "com.google.protobuf" % "protobuf-java" % "3.0.0-beta-3",
        "ch.qos.logback" % "logback-classic" % "1.1.6"
      ),
      scalafmtConfig in ThisBuild := Some(file(".scalafmt"))
    )
    .settings(reformatOnCompileSettings)


