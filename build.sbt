organization := "org.in-cal"

name := "incal-access-elastic"

version := "0.2.0"

description := "Provides a convenient access layer for Elastic Search based on Elastic4S library."

isSnapshot := false

scalaVersion := "2.11.12"

val esVersion = "5.6.10"

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % esVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-http" % esVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % esVersion,
  "javax.inject" % "javax.inject" % "1",
  "org.in-cal" %% "incal-core" % "0.2.0",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "org.slf4j" % "slf4j-api" % "1.7.21"
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
