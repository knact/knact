package io.knact.guard.server

import cats.implicits._
import cats.effect.IO
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import monix.eval.Task
import org.http4s.server.blaze._
import monix.execution.Scheduler.Implicits.global


object Main extends StreamApp[Task] {
	override def stream(args: List[String], requestShutdown: Task[Unit]): Stream[Task, ExitCode] = {

		val groupRepo = new GuardGroupRepo()
		val service = new APIService(groupRepo)

		BlazeBuilder[Task]
			.bindHttp(8080, "localhost")
			.mountService(service.services, "/api")
			.serve
	}
}