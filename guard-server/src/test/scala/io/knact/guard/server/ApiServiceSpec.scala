package io.knact.guard.server

import io.knact.guard._
import io.knact.guard.server.TaskAssertions.{SyncContext, given, jsonBody}
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{Method, Request, _}
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.java8.time._
import io.knact.guard.Entity.id
import io.knact.guard._
import org.http4s.circe._

import scala.collection.mutable.ArrayBuffer

class ApiServiceSpec extends FlatSpec with Matchers with EitherValues {

	private implicit val dsl: Http4sDsl[Task] = Http4sDsl.apply[Task]

	import dsl._

	behavior of "ApiService"

	it should "return empty with no groups" in SyncContext {
		given(new ApiService(new GuardGroupRepo(ArrayBuffer())).services,
			Request[Task](Method.GET, uri("/groups"))) { response =>
			response.status shouldBe Status.Ok
			jsonBody[Seq[Group]](response) {_.right.value shouldBe empty}
		}
	}

	it should "return groups with given groups" in SyncContext {

		val expected = Seq(
			Group(id(1), "foo", Nil, Nil),
			Group(id(42), "bar", Nil, Nil))

		given(new ApiService(new GuardGroupRepo(ArrayBuffer(expected :_*))).services,
			Request[Task](Method.GET, uri("/groups"))) { response =>
			response.status shouldBe Status.Ok
			jsonBody[Seq[Group]](response) {_.right.value shouldBe expected}
		}
	}

	// TODO test return some value where group is Group(id(1), "", Nil, Nil)::Nil

}