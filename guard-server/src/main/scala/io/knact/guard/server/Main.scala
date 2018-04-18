package io.knact.guard.server

import java.time.ZonedDateTime
import cats._
import cats.implicits._
import better.files.File
import com.typesafe.scalalogging.LazyLogging
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.knact.guard.server.service.{JsonNodeTargetService, LinuxSshPerfService}
import monix.eval.Task
import org.http4s.server.blaze._

import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration._
import scala.util.Try
import io.circe.syntax._
import io.circe.generic._
import io.circe.generic.auto._
import io.circe.parser.decode
import java.io

import monix.execution.Scheduler
import scopt.Read

object Main extends StreamApp[Task] with LazyLogging {


	case class CLIOpt(config: File = File(".") / "config.json",
					  target: File = File(".") / "targets.json")


	override def stream(args: List[String], requestShutdown: Task[Unit]): Stream[Task, ExitCode] = {


		implicit val fileRead: scopt.Read[File] = Read.reads(File(_))

		val options = new scopt.OptionParser[CLIOpt]("knact-guard") {
			head("kanct-guard", Version)
			opt[File]("config")
				.valueName("<file>")
				.action((x, opt) => opt.copy(config = x))
				.text("configuration file path, defaults to ./config.json")
			opt[File]("targets")
				.valueName("<file>")
				.action((x, opt) => opt.copy(target = x))
				.text("targets file path, defaults to ./targets.json")
		}.parse(args, CLIOpt())


		(for {
			opt <- options.toRight(new Exception("Bad args"))
			config <- decode[Config](opt.config.contentAsString)
				.leftMap { e => new RuntimeException(s"Error parsing ${opt.config}", e) }
		} yield (config, opt.target)) match {
			case Left(e)                     => Stream.raiseError(e)
			case Right((config, targetFile)) =>

				logger.info(s"Using target $targetFile and configuration $config")
				implicit val scheduler: Scheduler = Scheduler.forkJoin(
					name = "guard-server",
					parallelism = sys.runtime.availableProcessors(),
					maxThreads = config.serverMaxThread)

				val repos = new InMemoryContext(
					version = Version,
					startTime = ZonedDateTime.now())

				val service = new ApiService(repos, config)

				def migrate() = for {
					// TODO do actual migration and not just dummy data
					_ <- Task {logger.info("Migration started")}
					//					_ <- repos.nodes.insert(Node(id(1), SshKeyTarget("2", 22, "bar", Array(42)), "a"))
					//					_ <- repos.nodes.persist(n.right.get, ZonedDateTime.now(), Offline)
					_ <- Task {logger.info("Migration completed")}
				} yield ()

				//								def setupWatchdog() = MonkeyService(config, service.nodes)
				def setupWatchdog() = LinuxSshPerfService(config, service.nodes)

				def setupNodeWatch() = {
					targetFile.verifiedExists match {
						case Some(true) =>
							logger.info(s"Target file found $targetFile")
							JsonNodeTargetService(targetFile, repos.nodes)
						case _          =>
							logger.info(s"Targets file $targetFile does not exist, no targets will be loaded")
							Task.unit
					}
				}
				for {
					_ <- Stream.eval(setupWatchdog())
					_ <- Stream.eval(setupNodeWatch())
					_ <- Stream.eval(migrate())
					exit <- BlazeBuilder[Task]
						.bindHttp(config.port, "localhost")
						.mountService(service.services, "/api")
						.serve
				} yield exit
		}

	}
}