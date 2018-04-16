package io.knact.guard.server

import java.time.ZonedDateTime

import io.circe._
import io.knact.guard.{Bound, Entity, Line, Telemetry}
import io.knact.guard.Entity.{Node, _}
import io.knact.guard.Telemetry.Status
import io.knact.guard.server.TaskAssertions.SyncContext
import jdk.nashorn.internal.ir.annotations.Immutable
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.collection.immutable.TreeMap
import scala.collection.mutable

class RepositorySpec extends FlatSpec with Matchers with EitherValues {

	behavior of "NodeRepository"

	private def mkContext(): InMemoryContext = new InMemoryContext("0.0.1", ZonedDateTime.now())

	implicit val orderingLocalDateTime: Ordering[ZonedDateTime] = Ordering.by(d => (d.getYear, d.getDayOfYear))

	forAll(Table("repo",
		() => new InMemoryContext("0.0.1", ZonedDateTime.now()): ApiContext
	)) { f =>

		val target = SshPasswordTarget("a", 42, "a", "a")

		/*
		*   def insert(a: A): F[Failure | Id[A]]
	  * */
		it should "insert with incremented id" in SyncContext {
			val ctx = f()
			val node = Node(id(42), target, "foo")
			for {
				a <- ctx.nodes.insert(node)
				b <- ctx.nodes.insert(node)
			} yield {
				a.right.value shouldBe id(0)
				b.right.value shouldBe id(1)
			}
		}

		it should "insert discards id and relations" in SyncContext {
			val ctx = f()
			for {
				id <- ctx.nodes.insert(Node(id(42), target, "bar", None, Map()))
				found <- ctx.nodes.find(id.right.value)
			} yield found contains Node(Entity.id(0), target, "bar")
		}

		/*
		* 		def telemetries(nid: Id[Entity.Node])(bound: Bound): Task[Option[TelemetrySeries]]
		* */
		it should "return no telemetries when no telemetries are given" in SyncContext {
			val ctx = f()
			val node1 = Node(id(42), target, "foo")
			val node2 = Node(id(42), target, "bar")
			for {
				a <- ctx.nodes.insert(node1)
				b <- ctx.nodes.insert(node2)
				ts1 <- (ctx.nodes.telemetries(id(0))(Bound()))
				ts2 <- (ctx.nodes.telemetries(id(1))(Bound()))
			} yield {
				ts1 contains TelemetrySeries(id(0))
				ts2 contains TelemetrySeries(id(1))
			}
		}

		it should "return no logSeries when no LogSeries are given" in SyncContext {
			val ctx = f()
			val node1 = Node(id(42), target, "foo")
			val node2 = Node(id(42), target, "bar")
			for {
				a <- ctx.nodes.insert(node1)
				b <- ctx.nodes.insert(node2)
				ls1 <- (ctx.nodes.telemetries(id(0))(Bound()))
				ls2 <- (ctx.nodes.telemetries(id(1))(Bound()))
			} yield {
				ls1 contains TelemetrySeries(id(0))
				ls2 contains TelemetrySeries(id(1))
			}
		}

		it should "return the correct telemetries when some telemetries are given" in SyncContext {
			val ctx = f()
			//val lines : Seq[String] = Seq("foo", "bar")
			val status : Telemetry.Status = Telemetry.Offline

			val timeSeries : Entity.TimeSeries[Telemetry.Status] = TreeMap((ZonedDateTime.parse(
				"2007-12-03T10:15:30+01:00[Europe/Paris]"),
				status))

			val node = Node(id(42), target, "remark")
			for {
				id1 <- ctx.nodes.insert(node)
				id2 <- ctx.nodes.persist(
					id(0),
					ZonedDateTime.parse(
						"2007-12-03T10:15:30+01:00[Europe/Paris]"),
					Telemetry.Offline)

				ts <- ctx.nodes.telemetries(id(0))(Bound())
			} yield ts contains Entity.TelemetrySeries(id(0), timeSeries)
		}

		it should "return the correct logs when some logs are given" in SyncContext {
			val ctx = f()
			val lines : Seq[String] = Seq("foo", "bar")

			val timeSeries : Entity.TimeSeries[Seq[Line]] = TreeMap((ZonedDateTime.parse(
				"2007-12-03T10:15:30+01:00[Europe/Paris]"),
				lines))

			val node = Node(id(42), target, "remark")
			for {
				id1 <- ctx.nodes.insert(node)
				id2 <- ctx.nodes.persist(
					id(0),
					ZonedDateTime.parse(
						"2007-12-03T10:15:30+01:00[Europe/Paris]"),
					"path",
					lines)
				logs1 <- ctx.nodes.logs(id(0))("path")(Bound())
			} yield logs1 contains Entity.LogSeries(id(0), timeSeries)
		}


		it should "return the correct telemetries when some telemetries and bounds are given" in SyncContext {
			val ctx = f()
			val status : Telemetry.Status = Telemetry.Offline

			val timeSeries1 : Entity.TimeSeries[Telemetry.Status] = TreeMap((ZonedDateTime.parse(
				"2007-12-03T10:15:30+01:00[Europe/Paris]"),
				status))
			val timeSeries2 : Entity.TimeSeries[Telemetry.Status] = TreeMap((ZonedDateTime.parse(
				"2009-12-03T10:15:30+01:00[Europe/Paris]"),
				status))

			val node = Node(id(42), target, "remark")
			for {
				id1 <- ctx.nodes.insert(node)

				id2 <- ctx.nodes.persist(
					id(0),
					ZonedDateTime.parse(
						"2008-12-03T10:15:30+01:00[Europe/Paris]"),
					Telemetry.Offline)

				id2 <- ctx.nodes.persist(
					id(0),
					ZonedDateTime.parse(
						"2010-12-03T10:15:30+01:00[Europe/Paris]"),
					Telemetry.Offline)

				ts1 <- ctx.nodes.telemetries(id(0))(Bound(
					Some(ZonedDateTime.parse("2006-12-03T10:15:30+01:00[Europe/Paris]")),
					Some(ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"))))

				ts2 <- ctx.nodes.telemetries(id(0))(Bound(
					Some(ZonedDateTime.parse("2002-12-03T10:15:30+01:00[Europe/Paris]")),
					Some(ZonedDateTime.parse("2003-12-03T10:15:30+01:00[Europe/Paris]"))))

				ts3 <- ctx.nodes.telemetries(id(0))(Bound(
					Some(ZonedDateTime.parse("2002-12-03T10:15:30+01:00[Europe/Paris]")),
					Some(ZonedDateTime.parse("2009-12-03T10:15:30+01:00[Europe/Paris]"))))

				ts4 <- ctx.nodes.telemetries(id(0))(Bound(
					Some(ZonedDateTime.parse("2009-12-03T10:15:30+01:00[Europe/Paris]")),
					None))

				ts5 <- ctx.nodes.telemetries(id(0))(Bound(
					None,
					Some(ZonedDateTime.parse("2009-12-03T10:15:30+01:00[Europe/Paris]"))))


			} yield {
				ts1 contains Entity.TelemetrySeries(id(0), timeSeries1)
				ts1 contains Entity.TelemetrySeries(id(0), timeSeries2)

				!(ts2 contains Entity.TelemetrySeries(id(0), timeSeries1))
				!(ts2 contains Entity.TelemetrySeries(id(0), timeSeries2))

				ts3 contains Entity.TelemetrySeries(id(0), timeSeries1)
				!(ts3 contains Entity.TelemetrySeries(id(0), timeSeries2))

				!(ts4 contains Entity.TelemetrySeries(id(0), timeSeries1))
				ts4 contains Entity.TelemetrySeries(id(0), timeSeries2)

				ts5 contains Entity.TelemetrySeries(id(0), timeSeries1)
				!(ts5 contains Entity.TelemetrySeries(id(0), timeSeries2))

			}
		}


		it should "return the correct logs when some logs and bounds are given" in SyncContext {
			val ctx = f()
			val lines : Seq[String] = Seq("foo", "bar")

			val timeSeries : Entity.TimeSeries[Seq[Line]] = TreeMap((ZonedDateTime.parse(
				"2007-12-03T10:15:30+01:00[Europe/Paris]"),
				lines))

			val node = Node(id(42), target, "remark")
			for {
				id1 <- ctx.nodes.insert(node)
				id2 <- ctx.nodes.persist(
					id(0),
					ZonedDateTime.parse(
						"2007-12-03T10:15:30+01:00[Europe/Paris]"),
					"path",
					lines)
				logs1 <- ctx.nodes.logs(id(0))("path")(Bound())
			} yield logs1 contains Entity.LogSeries(id(0), timeSeries)
		}

		/*
		* 		def persist(nid: Id[Entity.Node],
		*			time: ZonedDateTime,
		* 		path: Path,
		* 		lines: Seq[Line]): Task[Failure | Id[Entity.Node]]
		*
		* */
		it should "return the correct id when persist is invoked I" in SyncContext {
			val ctx = f()
			val lines : Seq[String] = Seq("foo")
			val node = Node(id(42), target, "foo")
			for {
				id1 <- ctx.nodes.insert(node)
				id2 <- ctx.nodes.persist(
													id(0),
													ZonedDateTime.now, "bar", lines)
			} yield {
				id1 shouldBe id2
			}
		}

		/*
		* 		def find(id: Id[A]): F[Option[A]]
		* */
		it should "find inserted items I" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			val anId = Entity.id[Entity.Node, Int](6)
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				//anId <- Entity.id[Entity.Node, Int](6)
				found <- ctx.nodes.find(anId)
			} yield found contains Node(Entity.id(5), target, "foo")
		}

		/*
		* 		def find(target: Target) : Task[Option[Node]]
		* */
		it should "find inserted items II" in SyncContext {
			val ctx = f()

			val nodes = List(1 to 20:_*)
  			.map(p => SshPasswordTarget("a", p, "a", "a"))
  			.map(t => Node(id(42), t, "remark"))

			val target = SshPasswordTarget("a", 13, "a", "a")
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				found <-ctx.nodes.find(target)
			} yield found contains Node(id(12), target, "remark")
		}

		/*
		* 		def list(): Task[Seq[Id[A]]]
		* */
		it should "list all ids in any order" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			for {
				ids <- Task.traverse(nodes) {ctx.nodes.insert}
				gs <- ctx.nodes.list()
			} yield gs should contain theSameElementsAs ids.map {_.right.value}
		}
		/*
		*		def update(id: Id[A], f: A => A): F[Failure | Id[A]]
		* */
		it should "update an existing node" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			val new_ = Node(id(50), target, "bar")
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				id <- ctx.nodes.update(id(5), n => new_)
				n <- ctx.nodes.find(id.right.value)
			} yield n contains new_
		}

		it should "fail updating a non existing node" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			val new_ = Node(id(50), target, "bar")
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				fail <- ctx.nodes.update(id(15), n => new_)
			} yield fail contains "Node 15 does not exist"
		}

		/*
		*		def delete(id: Id[A]): F[Failure | Id[A]]
		* */
		it should "Delete an existing node" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				id <- ctx.nodes.delete(id(5))
				n <- ctx.nodes.find(id.right.value)
			} yield !(n contains Node(id.right.value, target, "foo"))
		}

		it should "fail deleting a non existing node" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				fail <- ctx.nodes.delete(id(15))
			} yield fail contains "Node 15 does not exist"
		}

/*
		it should "List all the ids stored" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			val observ = ctx.nodes.ids
			for {
				ids <- Task.traverse(nodes)(ctx.nodes.insert)
				o <- observ.subscribe
			} yield o should contain Node(id(42), target, "foo")
		}*/
	}

}