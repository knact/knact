
import Common._
import Dependencies.{ScalaTest, _}

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
		ScalaLogging,
		"com.hierynomus" % "sshj" % "0.23.0"
		exclude("org.bouncycastle", "bcprov-jdk15on")
		exclude("org.bouncycastle", "bcpkix-jdk15on"),
		// XXX this is needed because sshj has it as compile dependency
		"org.bouncycastle" % "bcprov-jdk15on" % "1.56" ,
		"org.bouncycastle" % "bcpkix-jdk15on" % "1.56" ,
		"org.apache.sshd" % "sshd-core" % "1.6.0" % Test,
		"com.google.jimfs" % "jimfs" % "1.1" % Test,
		ScalaTest % Test
	),
	//	dependencyOverrides ++= Seq(
	//		// XXX this is needed because sshj has it as compile dependency
	//		"org.bouncycastle" % "bcprov-jdk15on" % "1.56" % Provided,
	//		"org.bouncycastle" % "bcpkix-jdk15on" % "1.56" % Provided,
	//	)
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

lazy val guardCommonSettings = commonSettings ++ Seq(
	unmanagedSourceDirectories in Compile += {
		baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala"
	},
	unmanagedSourceDirectories in Test += {
		baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
	},
	libraryDependencies ++= circe ++ Seq(
		Enumeratum,
		Monix,
		Squants,
		ScalaTest % Test
	)
)

lazy val `guard-common-js` = project.in(file("guard-common/js"))
	.settings(guardCommonSettings)
	.enablePlugins(ScalaJSPlugin)

lazy val `guard-common-jvm` = project.in(file("guard-common/jvm"))
	.settings(
		guardCommonSettings,
		libraryDependencies ++= http4sClient ++ Seq(
			"org.glassfish.tyrus.bundles" % "tyrus-standalone-client-jdk" % "1.13.1"
		)
	)

lazy val `guard-server` = project.settings(
	commonSettings,
	libraryDependencies ++= http4sServer ++ Seq(
		"com.github.scopt" %% "scopt" % "3.7.0",
		Guava,
		BetterFiles,
		ScalaLogging, Logback,
		ScalaTest % Test)
).dependsOn(
	`guard-common-jvm`,
	`core-ssh-transport`,
	`core-linux-perf`,
)

lazy val `guard-client-cli` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		Cats,
		ScalaLogging,
		"com.googlecode.lanterna" % "lanterna" % "3.0.0",
	)
).dependsOn(`guard-common-jvm`)

lazy val `guard-client-jfx` = project.settings(
	commonSettings,
	libraryDependencies ++= Seq(
		Cats,
		Guava,
		ScalaLogging, Logback,
		"org.scalafx" %% "scalafx" % "8.0.144-R12",
		"org.scalafx" %% "scalafxml-core-sfx8" % "0.4",
		"org.fxmisc.easybind" % "easybind" % "1.0.3",
		"org.controlsfx" % "controlsfx" % "8.40.14",
		"se.sawano.java" % "alphanumeric-comparator" % "1.4.1",
		ScalaTest % Test,
	),
).dependsOn(`guard-common-jvm`)

lazy val `guard-client-web` = project.settings(
	commonSettings,
	scalaJSUseMainModuleInitializer := true,
	skip in packageJSDependencies := false,
	LessKeys.compress in Assets := true,
	workbenchStartMode := WorkbenchStartModes.Manual,
	libraryDependencies ++= Seq(
		Cats,
		ScalaTest % Test,
	),
	jsDependencies ++= Seq(),
).enablePlugins(ScalaJSPlugin)
	.dependsOn(`guard-common-js`)


//lazy val knact = (project in file("."))
//	.aggregate(core, )