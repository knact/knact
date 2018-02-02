import sbt.Keys._

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
			"-Ywarn-unused",
			"-Ypartial-unification",
		),
		javacOptions ++= Seq(
			"-target", "1.8",
			"-source", "1.8",
			"-Xlint:deprecation"),
	)


}
