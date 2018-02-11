package io.knact.guard.server

import java.time.ZonedDateTime

import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.knact.guard.{Entity, Group, Node}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.server.blaze._

import scala.collection.mutable.ArrayBuffer
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
				val groupRepo = new GuardGroupRepo()
				val service = new APIService(groupRepo)
				for {
					// TODO migration and stuff
					exit <- BlazeBuilder[Task]
						.bindHttp(port, "localhost")
						.mountService(service.services, "/api")
						.serve
				} yield exit
		}

	}
}