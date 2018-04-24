package io.knact.guard.server

import java.time.ZonedDateTime

import io.knact.guard.Entity

import scala.collection.immutable.TreeMap

//import io.knact.guard.Entity
import cats.kernel.instances.EitherEq
import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.knact.guard.Entity.{id, _}
import io.knact.guard._
import io.knact.guard.server.TaskAssertions.{SyncContext, given, jsonBody}
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{Request, _}
import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.concurrent.duration._

class ApiServiceSpec extends FlatSpec with Matchers with EitherValues {

	private implicit val dsl: Http4sDsl[Task] = Http4sDsl.apply[Task]

	import dsl._

	behavior of "ApiService"

	val bounds = "?start=2007-12-03T10:15:30+01:00[Europe/Paris]&" +
				 "end=2009-12-03T10:15:30+01:00[Europe/Paris]" // TODO: bounds are not decoded properly!

	val target = SshPasswordTarget("a", 42, "a", "a")

	val getCtx = () => new InMemoryContext(
		version = "0.0.1",
		startTime = ZonedDateTime.now())

	//val context = getCtx()

	val GetmkEndpoints: InMemoryContext => HttpService[Task] = ctx =>
		new ApiService(
			ctx,
			config = Config(42, 1 second)).services

	// <NodeService Tests>
	it should "return empty with no nodes" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		given(mkEndpoints, Request[Task](GET, uri(NodePath))) { response =>
			response.status shouldBe Ok
			jsonBody[Seq[Node]](response) {_.right.value shouldBe empty}
		}
	}

	it should "return all the nodes when some nodes are present" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](GET, uri(NodePath))) { response =>
			response.status shouldBe Ok
			jsonBody[List[Id[Node]]](response) { xs =>
				xs.right.value should contain theSameElementsAs (0 to 9)
				//                xs.right.value shouldBe nodes
			}
		}
	}

	it should "return NotFound when a particular node is requested, but no nodes are stored" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		given(mkEndpoints, Request[Task](GET, uri("node/6"))) { response =>
			response.status shouldBe NotFound //Doesnt work
		}
	}

	it should "return NotFound when a particular node is requested, but it is not stored" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](GET, uri("node/26"))) { response =>
			response.status shouldBe NotFound //Doesnt work
		}
	}

	it should "return the correct node when it is requested and stored" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](GET, uri("node/6"))) { response =>
			response.status shouldBe Ok
			jsonBody[Node](response) {_.right.value shouldBe Node(id(6), target, "foo")}
		}
	}

	ignore should "add a node when requested to" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val body: EntityBody[Task] = ??? // <- TODO implement this

		given(mkEndpoints, Request[Task](POST, uri(NodePath), body = body)) { response =>
			response.status shouldBe Ok
			jsonBody[Id[Node]](response) {_.right.value shouldBe 0}
		}
	}

	ignore should "update a node when requested to" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val body: EntityBody[Task] = ??? // <- TODO implement this
		val node = Node(io.knact.guard.Entity.id[io.knact.guard.Entity.Node, Long](42), target, "foo")
		ctx.nodes.insert(node).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](POST, uri("node/0"), body = body)) { response =>
			response.status shouldBe Ok
			jsonBody[Id[Node]](response) {_.right.value shouldBe 0}
		}
	}


	it should "return BadRequest when requested to update a node which doesn't exist" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](POST, uri("node/15"))) { response =>
			response.status shouldBe BadRequest
		}
	}


	it should "delete a node when requested to" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](DELETE, uri("node/8"))) { response =>
			response.status shouldBe Ok
			jsonBody[Altered[Id[Node]]](response) {_.right.value shouldBe Altered(id(8))}
		}
	}


	it should "return BadRequest when attempting to delete a non-existing node" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](DELETE, uri("node/18"))) { response =>
			response.status shouldBe BadRequest
		}
	}


	/*
	* 	Timeseries:
	* */
	// FIXME
	ignore should "return telemetries on request" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val status = Telemetry.Offline
		val time = ZonedDateTime.parse("2008-12-03T10:15:30+01:00[Europe/Paris]")

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		val expectedTelSeries = TelemetrySeries(
			id(6),
			TreeMap(time -> status)
		)

		ctx.nodes.persist(
			id(6),
			time,
			status
		)

		val uri = Uri.unsafeFromString("node/6/telemetry/" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe Ok
			jsonBody[TelemetrySeries](response) {_ shouldBe expectedTelSeries}
		}
	}


	it should "return notFound when tel.Series are requested but node does not exist" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val status = Telemetry.Offline

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		val uri = Uri.unsafeFromString("node/16/telemetry/" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe NotFound
		}
	}

	it should "return notFound when tel.Series are requested but node has no tel.Series logged in bounds" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val status = Telemetry.Offline

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		ctx.nodes.persist(
			id(6),
			ZonedDateTime.parse("2003-12-03T10:15:30+01:00[Europe/Paris]"),
			status)

		val uri = Uri.unsafeFromString("node/6/telemetry/" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe NotFound
		}
	}


	/*
	* 	LogSeries:
	* */
	// FIXME
	ignore should "return logSeries on request" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val lines: Seq[Line] = Seq("foo", "bar")
		val time = ZonedDateTime.parse("2008-12-03T10:15:30+01:00[Europe/Paris]")

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		ctx.nodes.persist(
			id(6),
			time,
			"path",
			lines)

		val expectedLogs = LogSeries(
			id(6),
			TreeMap(time -> lines))

		val uri = Uri.unsafeFromString("node/6/log/path" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe Ok
			jsonBody[LogSeries](response) {_ shouldBe expectedLogs}
		}
	}

	it should "return notFound when LogSeries are requested, but node does not exist" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val lines: Seq[Line] = Seq("foo", "bar")

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		val uri = Uri.unsafeFromString("node/16/log/path" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe NotFound
		}
	}

	it should "return notFound when LogSeries are requested but node has no Logs in Bounds" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)
		val lines: Seq[Line] = Seq("foo", "bar")
		val time = ZonedDateTime.parse("2003-12-03T10:15:30+01:00[Europe/Paris]")

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		ctx.nodes.persist(
			id(6),
			time,
			"path",
			lines)

		val expectedLogs = LogSeries(
			id(6),
			TreeMap(time -> lines))

		val uri = Uri.unsafeFromString("node/6/log/path" + bounds)

		given(mkEndpoints, Request[Task](GET, uri)) { response =>
			response.status shouldBe NotFound
		}
	}

	//TODO returns a 501 Not implemented response instead of 200 OK
	// FIXME
	ignore should "return events on request" in SyncContext {
		val ctx = getCtx()
		val mkEndpoints = GetmkEndpoints(ctx)

		val nodes = List.fill(10) {Node(id(42), target, "foo")}
		Task.traverse(nodes)(ctx.nodes.insert).runSyncUnsafe(3000 seconds)

		given(mkEndpoints, Request[Task](GET, uri("node/events"))) { response =>
			response.status shouldBe Ok
		}

	}
	// </nodeService Tests>

}