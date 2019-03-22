organization := "org.in-cal"

name := "incal-access-elastic"

version := "0.1.3"

description := "Provides a convenient access layer for Elastic Search."

isSnapshot := false

scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "2.3.0",
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % "2.3.0",
  "com.typesafe.play" %% "play-json" % "2.5.9",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.in-cal" %% "incal-core" % "0.1.2"
)

// POM settings for Sonatype
homepage := Some(url("https://in-cal.org"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/in-cal/incal-access-elastic"), "scm:git@github.com:in-cal/incal-access-elastic.git"))

developers := List(Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")))

licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
