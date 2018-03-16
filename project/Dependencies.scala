import sbt._


object Dependencies {

	lazy val FindBugsJsr305 = "com.google.code.findbugs" % "jsr305" % "3.0.2"
	lazy val Guava          = "com.google.guava" % "guava" % "23.6-jre"

	lazy val Cats  = "org.typelevel" %% "cats-core" % "1.0.1"
	lazy val Monix = "io.monix" %% "monix" % "3.0.0-M3"

	lazy val ScalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
	lazy val Logback      = "ch.qos.logback" % "logback-classic" % "1.2.3"

	lazy val Enumeratum = "com.beachape" %% "enumeratum" % "1.5.12"

	lazy val ScalaTest   = "org.scalatest" %% "scalatest" % "3.0.4"
	lazy val MockitoCore = "org.mockito" % "mockito-core" % "2.10.0"

	lazy val Squants = "org.typelevel"  %% "squants"  % "1.3.0"

	val http4sVersion = "0.18.0"
	val circeVersion  = "0.9.1"

	lazy val http4sServer = Seq(
		"org.http4s" %% "http4s-dsl",
		"org.http4s" %% "http4s-blaze-server",
		"org.http4s" %% "http4s-blaze-client",
		"org.http4s" %% "http4s-circe",
	).map {_ % http4sVersion}

	lazy val http4sClient = Seq(
		"org.http4s" %% "http4s-blaze-client",
		"org.http4s" %% "http4s-circe",
	).map {_ % http4sVersion}


	lazy val circe = Seq(
		"io.circe" %% "circe-core",
		"io.circe" %% "circe-parser",
		"io.circe" %% "circe-refined",
		"io.circe" %% "circe-java8",
		"io.circe" %% "circe-generic",
		"io.circe" %% "circe-generic-extras",
	).map {_ % circeVersion}


}
