logLevel := Level.Warn

addSbtPlugin("com.geirsson" %% "sbt-scalafmt" % "0.2.11")

addSbtPlugin("com.trueaccord.scalapb" % "sbt-scalapb" % "0.5.32")

// Build-time dependency, used to avoid relying on a native library for protobuf code generation.
libraryDependencies += "com.github.os72" % "protoc-jar" % "3.0.0-b2.1"

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.6.0")