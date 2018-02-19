package io.knact.guard.server

import java.time.ZonedDateTime

import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.knact.guard.Entity
import io.knact.guard.Entity._
import io.knact.guard.Telemetry.Offline
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.server.blaze._

import scala.util.Try


object Main extends StreamApp[Task] {

	case class Config(port: Int)

	override def stream(args: List[String], requestShutdown: Task[Unit]): Stream[Task, ExitCode] = {

		val config = for {
			// TODO other parameters
			port <- Try {sys.env.getOrElse("port", "8080").toInt}.toEither
		} yield Config(port)

		config match {
			case Left(e)             => Stream.raiseError(e)
			case Right(Config(port)) =>
				val repos = new InMemoryRepository()
				val service = new ApiService(repos)

				def migrate() = for {
					// TODO do actual migration and not just dummy data
					a <- repos.groups.insert(Group(id(1), "b", Nil))
					n <- repos.nodes.insert(Node(id(1), a.right.get, SshKeyTarget("1", 22, Array(42)), "a"))
					_ <- repos.nodes.persist(n.right.get, ZonedDateTime.now(), Offline)
				} yield ()

				for {
					_ <- Stream.eval(migrate())
					exit <- BlazeBuilder[Task]
						.bindHttp(port, "localhost")
						.mountService(service.services, "/api")
						.serve
				} yield exit
		}

	}
}