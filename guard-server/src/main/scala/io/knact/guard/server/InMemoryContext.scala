package io.knact.guard.server

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

import cats.implicits._
import io.knact.guard.Entity._
import io.knact.guard.Telemetry.{Online, Verdict}
import io.knact.guard.server.InMemoryContext.MapBackedRepository
import io.knact.guard.{Bound, Entity, Failure, Line, NodeRepository, Path, ProcedureRepository, Repository, Telemetry, |}
import monix.eval.Task
import monix.reactive.Observable
import monix.reactive.subjects.ConcurrentSubject

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
  * An in-memory implementation of all the repositories the API needs
  */
class InMemoryContext(override val version: String,
					  override val startTime: ZonedDateTime) extends ApiContext {

	private final val idCounter: AtomicLong = new AtomicLong(0)

	private val nodeStore     : mutable.Map[Id[Node], NodeEntry]      = TrieMap()
	private val procedureStore: mutable.Map[Id[Procedure], Procedure] = TrieMap()


	val procedures: ProcedureRepository = new ProcedureRepository
		with MapBackedRepository[Procedure, Procedure] {
		override val buffer: mutable.Map[Id[Procedure], Procedure] = procedureStore
		override def counter: AtomicLong = idCounter
		override def entityName: String = "Procedure"
		override def extract: Procedure => Procedure = identity
		override def resolve: Procedure => (Failure | Procedure) = Right(_)
	}

	// technically, series should be a monoid
	case class NodeEntry(node: Node, telemetries: TelemetrySeries, logs: Map[Path, LogSeries])
	val nodes: NodeRepository = new NodeRepository
		with MapBackedRepository[Node, NodeEntry] {
		override val buffer: mutable.Map[Id[Node], NodeEntry] = nodeStore
		override def counter: AtomicLong = idCounter
		override def entityName: String = "Node"
		override def extract: NodeEntry => Node = { entry =>

			// fold through the summary, discarding(fusing) points that have the same status
			// comparing to the previous point
			val summarised = entry.telemetries.series
				.foldLeft(Vector[(ZonedDateTime, Option[Verdict])]()) { case (acc, (time, status)) =>
					val state = status match {
						case Online(s, _, _) => Some(s)
						case _               => None
					}
					// TODO test me
					acc match {
						case xs :+ ((_, lastState)) =>
							if (lastState == state) xs
							else xs :+ (time -> state)
						case IndexedSeq()           => Vector(time -> state)
					}
				}
			entry.node.copy(
				telemetries = summarised.toMap,
				// sum up all the lengths of the log lines
				logs = entry.logs.mapValues {_.series.values.map {_.length.toLong}.sum}
			)
		}
		override def resolve: Node => (Failure | NodeEntry) = node => buffer.get(node.id) match {
			case Some(value) => Right(value.copy(node = node))
			case None        => Right(NodeEntry(node, TelemetrySeries(node.id, Map()), Map()))
		}

		private val poolSubject      = ConcurrentSubject.publish[Set[Id[Node]]](scheduler)
		private val telemetrySubject = ConcurrentSubject.publish[Id[Node]](scheduler)
		private val logSubject       = ConcurrentSubject.publish[Id[Node]](scheduler)


		override def ids: Observable[Set[Id[Node]]] = poolSubject.distinctUntilChanged
		override def telemetryDelta: Observable[Id[Node]] = telemetrySubject
		override def logDelta: Observable[Id[Node]] = logSubject

		private def filterSeries[A](bound: Bound, s: TimeSeries[A]) = {
			bound match {
				case Bound(Some(start), Some(end)) => s.filter { case (k, _) =>
					k.isAfter(start) && k.isBefore(end)
				}
				case Bound(Some(start), None)      => s.filter { case (k, _) => k.isAfter(start) }
				case Bound(None, Some(end))        => s.filter { case (k, _) => k.isBefore(end) }
				case Bound(None, None)             => s
			}
		}

		override def telemetries(nid: Id[Node])
								(bound: Bound): Task[Option[TelemetrySeries]] = Task {
			buffer.get(nid).map { x =>
				// XXX use lens here
				x.telemetries.copy(series = filterSeries(bound, x.telemetries.series))
			}
		}

		override def logs(nid: Id[Node])
						 (path: Path)
						 (bound: Bound): Task[Option[LogSeries]] = Task {
			for {
				entry <- buffer.get(nid)
				logs <- entry.logs.get(path)
				// XXX use lens here
			} yield logs.copy(series = filterSeries(bound, logs.series))

		}
		override def execute(nid: Id[Node])(pid: Id[Procedure]): Task[|[Failure, String]] = ???

		override def persist(nid: Id[Node],
							 time: ZonedDateTime,
							 status: Telemetry.Status): Task[Failure | Id[Node]] = Task {
			val op = buffer.get(nid)
			op.foreach { x =>
				// XXX use lens here
				val appended = x.telemetries.series + (time -> status)
				buffer.update(nid, x.copy(
					telemetries = x.telemetries.copy(series = appended)))
				telemetrySubject.onNext(nid)
			}
			op.map { _ => nid }.toRight(notFound(nid))
		}

		override def persist(nid: Id[Node],
							 time: ZonedDateTime,
							 path: Path,
							 lines: Seq[Line]): Task[Failure | Id[Node]] = Task {
			val op = buffer.get(nid)
			op.foreach { x =>
				// XXX use lens here
				val updated = x.logs.get(path) match {
					case Some(ls) => LogSeries(nid, ls.series + (time -> lines))
					case None     => LogSeries(nid, Map(time -> lines))
				}
				buffer.update(nid, x.copy(logs = x.logs.updated(path, updated)))
				logSubject.onNext(nid)
			}
			op.map { _ => nid }.toRight(notFound(nid))
		}


		def notifyPoolChanged[A](task: Task[Failure | A]): Task[Failure | A] = {
			for {
				v <- task
				_ <-
					v match {
						case Left(_)  => Task.unit
						case Right(_) => list().map { ls => poolSubject.onNext(ls.toSet); () }
					}

			} yield v
		}
		override def insert(a: Node): Task[Failure | Id[Node]] =
			notifyPoolChanged(super.insert(a))
		override def delete(id: Id[Node]): Task[Failure | Id[Node]] =
			notifyPoolChanged(super.delete(id))
		override def update(id: Id[Node], f: Node => Node): Task[Failure | Id[Node]] =
			notifyPoolChanged(super.update(id, f))
	}

}

object InMemoryContext {
	/**
	  * A map backed repository that covers generic CRUD tasks
	  * @tparam E the entity type(where {{{ Id[K] }}} is the key of the map)
	  * @tparam V the stored type(value of the map)
	  */
	trait MapBackedRepository[E <: Entity[E], V] extends Repository[E, Task] {
		def buffer: mutable.Map[Id[E], V] //= mutable.Map()
		// the unique id generator
		def counter: AtomicLong
		// entity name used for failure messages
		def entityName: String
		// given some value, derive the entity
		def extract: V => E
		// given some entity, lift it into the stored type
		def resolve: E => Failure | V
		protected def notFound(id: Id[E]) = s"$entityName $id does not exist"
		override def list(): Task[Seq[Id[E]]] = Task {buffer.keys.toSeq}
		override def find(id: Id[E]): Task[Option[E]] = Task {buffer.get(id).map {extract}}
		override def insert(a: E): Task[Failure | Id[E]] = Task {
			val created = a.withId(a, Entity.id(counter.getAndIncrement()))
			val resolved = resolve(created)
			resolved.foreach { x => buffer += (created.id -> x) } // XXX has side effect
			resolved.map { _ => created.id }
		}
		override def delete(id: Id[E]): Task[Failure | Id[E]] = Task {
			buffer.get(id) match {
				case None    => Left(notFound(id))
				case Some(_) =>
					buffer -= id // XXX has side effect
					Right(id)
			}
		}
		override def update(id: Id[E], f: E => E): Task[Failure | Id[E]] = Task {
			val outcome = for {
				found <- buffer.get(id).toRight(notFound(id))
				resolved <- resolve(f(extract(found)))
			} yield resolved
			outcome.foreach { x => buffer.update(id, x) } // XXX has side effect
			outcome.map { _ => id }
		}
	}
}
