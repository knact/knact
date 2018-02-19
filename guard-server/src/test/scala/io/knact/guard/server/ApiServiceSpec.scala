package io.knact.guard.server

import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.knact.guard.Entity.Group
import io.knact.guard.server.TaskAssertions.{SyncContext, given, jsonBody}
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{Request, _}
import org.scalatest.{EitherValues, FlatSpec, Matchers}


class ApiServiceSpec extends FlatSpec with Matchers with EitherValues {

	private implicit val dsl: Http4sDsl[Task] = Http4sDsl.apply[Task]

	import dsl._

	behavior of "ApiService"

	private def mkEndpoints: HttpService[Task] =
		new ApiService(new InMemoryRepository()).services

	it should "return empty with no groups" in SyncContext {
		given(mkEndpoints, Request[Task](GET, uri("/group"))) { response =>
			response.status shouldBe Ok
			jsonBody[Seq[Group]](response) {_.right.value shouldBe empty}
		}
	}

	// TODO test return some value where group is Group(id(1), "", Nil, Nil)::Nil

}