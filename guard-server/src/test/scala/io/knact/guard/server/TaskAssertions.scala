package io.knact.guard.server

import cats.FlatMap
import io.circe.{Decoder, DecodingFailure, Json}
import io.knact.guard.Group
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService, Request, Response}
import org.http4s.circe._

import scala.concurrent.Await
import scala.concurrent.duration._

object TaskAssertions {

	object SyncContext {
		def apply[A](ioa: Task[A]): Unit = Await.result(ioa.runAsync, 30 seconds)
	}

	final def given(service: HttpService[Task], request: Request[Task])
				   (f: Response[Task] => Unit)
				   (implicit ev: Http4sDsl[Task]): Task[Unit] = {
		import ev._
		service.orNotFound.run(request).map(f)
	}

	final def jsonBody[T](response: Response[Task])(f: Either[DecodingFailure, T] => Unit)
						 (implicit dec: Decoder[T]): Unit = SyncContext {
		response.as[Json].map { v => f(v.as[T]) }
	}


}
