package io.knact.guard.server

import fs2.{Stream, Task}
import org.http4s.server.blaze._
import org.http4s.util.StreamApp

object Main extends StreamApp {
	override def stream(args: List[String]): Stream[Task, Nothing] = {
		BlazeBuilder
			.bindHttp(8080, "localhost")
//			.mountService(services, "/api")
			.serve
	}
}