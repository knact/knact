package io.knact.guard.server

import java.net.InetAddress
import java.time.ZonedDateTime

import com.typesafe.scalalogging.LazyLogging
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.knact.guard.Entity._
import io.knact.guard.Telemetry.Offline
import io.knact.ssh._
import monix.eval.Task
import org.http4s.server.blaze._

import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration._
import scala.util.Try


object Main extends StreamApp[Task] with LazyLogging {


	case class Config(port: Int, eventInterval: FiniteDuration)


	override def stream(args: List[String], requestShutdown: Task[Unit]): Stream[Task, ExitCode] = {
		val config = for {
			// TODO other parameters
			port <- Try {sys.env.getOrElse("port", "8080").toInt}.toEither
			eventInterval <- Try {
				Duration(sys.env.getOrElse("eventInterval", "1s")) match {
					case d: Infinite       =>
						throw new IllegalArgumentException(s"Infinite duration $d is unsupported")
					case d: FiniteDuration => d
				}
			}.toEither
		} yield Config(port, eventInterval)

		config match {
			case Left(e)                       => Stream.raiseError(e)
			case Right(config@Config(port, _)) =>
				val repos = new InMemoryContext(
					version = "0.0.1",
					startTime = ZonedDateTime.now())

				val service = new ApiService(repos, config)

				def migrate() = for {
					// TODO do actual migration and not just dummy data
					_ <- Task {logger.info("Migration started")}
					n <- repos.nodes.insert(Node(id(1), SshKeyTarget("1", 22, "foo", Array(42)), "a"))
					_ <- repos.nodes.insert(Node(id(1), SshKeyTarget("2", 22, "bar", Array(42)), "a"))
					_ <- repos.nodes.persist(n.right.get, ZonedDateTime.now(), Offline)
					_ <- Task {logger.info("Migration completed")}
				} yield ()

				def setupWatchdog() = MonkeyModule(service.nodes)

				for {
					_ <- Stream.eval(migrate())
					_ <- Stream.eval(setupWatchdog())
					exit <- BlazeBuilder[Task]
						.bindHttp(port, "localhost")
						.mountService(service.services, "/api")
						.serve
				} yield exit
		}

	}
}