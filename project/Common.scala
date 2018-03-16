import sbt.Keys._
import sbt._
import sbt.librarymanagement.CrossVersion

object Common {

	lazy val commonSettings = Seq(
		organization := "knact.io",
		version := "0.1.0-SNAPSHOT",
		scalaVersion := "2.12.4",
		scalacOptions ++= Seq(
			"-target:jvm-1.8",
			"-encoding", "UTF-8",
			"-unchecked",
			"-deprecation",
			"-Xfuture",
			"-Yno-adapted-args",
			"-Ywarn-dead-code",
			"-Ywarn-numeric-widen",
			"-Ywarn-value-discard",
//			"-Ywarn-unused", // TODO turn this back on for prod
			"-Ypartial-unification",
//			"-Xlog-implicits",
		),
		javacOptions ++= Seq(
			"-target", "1.8",
			"-source", "1.8",
			"-Xlint:deprecation"),
		addCompilerPlugin(
			"org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
		)
	)


}
