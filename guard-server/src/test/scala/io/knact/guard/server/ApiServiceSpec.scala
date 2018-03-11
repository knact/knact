package io.knact.guard.server

import java.time.ZonedDateTime

import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.knact.guard.Entity._
import io.knact.guard._
import io.knact.guard.server.Main.Config
import io.knact.guard.server.TaskAssertions.{SyncContext, given, jsonBody}
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{Request, _}
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import concurrent.duration._

class ApiServiceSpec extends FlatSpec with Matchers with EitherValues {

	private implicit val dsl: Http4sDsl[Task] = Http4sDsl.apply[Task]

	import dsl._

	behavior of "ApiService"

	private def mkEndpoints: HttpService[Task] =
		new ApiService(new InMemoryContext(
			version = "0.0.1",
			startTime = ZonedDateTime.now()),
			config = Config(42, 1 second)).services

	it should "return empty with no nodes" in SyncContext {
		given(mkEndpoints, Request[Task](GET, uri(NodePath))) { response =>
			response.status shouldBe Ok
			jsonBody[Seq[Node]](response) {_.right.value shouldBe empty}
		}
	}

	// TODO test return some value where group is Group(id(1), "", Nil, Nil)::Nil

}