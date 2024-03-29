import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

organization := "org.in-cal"

name := "incal-access-elastic"

version := "0.3.1-SNAPSHOT"

description := "Provides a convenient access layer for Elastic Search based on Elastic4S library."

isSnapshot := true

scalaVersion := "2.11.12" // "2.12.10"

val esVersion = "5.6.10"

libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % esVersion exclude("com.vividsolutions" ,"jts"), // jts is LGPL licensed (up to version 1.14)
  "com.sksamuel.elastic4s" %% "elastic4s-http" % esVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % esVersion,
  "javax.inject" % "javax.inject" % "1",
  "org.in-cal" %% "incal-core" % "0.3.0",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

// For licenses not automatically downloaded (need to list them manually)
licenseOverrides := {
  case
    DepModuleInfo("com.carrotsearch", "hppc", "0.7.1")
  | DepModuleInfo("commons-codec", "commons-codec", "1.10")
  | DepModuleInfo("commons-io", "commons-io", "2.6")
  | DepModuleInfo("commons-logging", "commons-logging", "1.1.3")
  | DepModuleInfo("org.apache.commons", "commons-lang3", "3.5")
  | DepModuleInfo("org.apache.logging.log4j", "log4j-api", "2.9.1") =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  case DepModuleInfo("org.slf4j", "slf4j-api", _) =>
    LicenseInfo(LicenseCategory.MIT, "MIT", "http://opensource.org/licenses/MIT")
}

// POM settings for Sonatype
homepage := Some(url("https://in-cal.org"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/in-cal/incal-access-elastic"), "scm:git@github.com:in-cal/incal-access-elastic.git"))

developers := List(Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")))

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)