
import Common._
import Dependencies.{ScalaTest, _}
import sbt.librarymanagement.CrossVersion

lazy val core = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		Monix,
		Cats,
		"net.sf.expectit" % "expectit-core" % "0.9.0",
		ScalaTest % Test
	)
)

lazy val `core-ssh-transport` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		"com.hierynomus" % "sshj" % "0.23.0",
		"org.apache.sshd" % "sshd-core" % "1.6.0" % Test,
		"com.google.jimfs" % "jimfs" % "1.1" % Test,
		ScalaTest % Test
	)
).dependsOn(core)


lazy val `core-linux-perf` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		"com.lihaoyi" %% "fastparse" % "1.0.0",
		"com.lihaoyi" %% "pprint" % "0.5.3",
		ScalaTest % Test
	)
).dependsOn(core)

lazy val `core-sample` = project.settings(
	commonSettings,
).dependsOn(core, `core-linux-perf`, `core-ssh-transport`)

lazy val `guard-client-common` = project.settings(
	commonSettings,
	libraryDependencies ++= circe ++ Seq(Enumeratum, ScalaTest % Test)
).dependsOn(core)

val http4sVersion = "0.18.0"

scalacOptions += "-Ypartial-unification"

lazy val `guard-server` = project.settings(
	commonSettings,
	libraryDependencies ++= http4s ++ Seq(Logback, ScalaTest % Test) ++ Seq(

		// Start with this one
		"org.tpolecat" %% "doobie-core"      % "0.5.0",

		// And add any of these as needed
		"org.tpolecat" %% "doobie-h2"        % "0.5.0", // H2 driver 1.4.196 + type mappings.
		"org.tpolecat" %% "doobie-hikari"    % "0.5.0", // HikariCP transactor.
		"org.tpolecat" %% "doobie-postgres"  % "0.5.0", // Postgres driver 42.2.1 + type mappings.
		"org.tpolecat" %% "doobie-specs2"    % "0.5.0", // Specs2 support for typechecking statements.
		"org.tpolecat" %% "doobie-scalatest" % "0.5.0"  // ScalaTest support for typechecking statements.

	)
).dependsOn(core, `guard-client-common`)

lazy val `guard-client-cli` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		"com.googlecode.lanterna" % "lanterna" % "3.0.0",
	)
).dependsOn(core, `guard-client-common`)

lazy val `guard-client-jfx` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		Guava,
		ScalaLogging, Logback,
		"org.scalafx" %% "scalafx" % "8.0.144-R12",
		"org.scalafx" %% "scalafxml-core-sfx8" % "0.4",
		ScalaTest % Test,
	),
).dependsOn(core, `guard-client-common`)

lazy val `guard-client-web` = project.settings(
	commonSettings,
	// TODO add dependencies for web module
).dependsOn(core, `guard-client-common`)


//lazy val knact = (project in file("."))
//	.aggregate(core, )