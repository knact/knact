package io.knact.guard

import java.time.{Duration, ZonedDateTime}

import cats.{Eq, Semigroup}
import shapeless.tag
import shapeless.tag.@@
import io.circe.java8.time._
import io.circe.generic.auto._
import io.knact.guard._
import io.knact.guard.Entity.Id
import io.knact.guard.Telemetry.{Status, Verdict}

import scala.collection.immutable.TreeMap

sealed trait Entity[A] {
	def id: Id[A]
	def withId(a: A, that: Id[A]): A
}

object Entity {

	// refined id
	type Id[A] = Long @@ A

	implicit def idEq[A]: Eq[Id[A]] = _ == _
	implicit def idOrd[A]: Ordering[Id[A]] = _ compareTo _

	def id[T, B](id: B)(implicit ev: Numeric[B]): Id[T] = tag[T][Long](ev.toLong(id))

	sealed trait Target {
		def host: String
		def port: Int
	}


	case class SshPasswordTarget(host: String, port: Int,
								 username: String, password: String) extends Target {
		override def toString: String = s"SshPasswordTarget($username@$host:$port)"
	}

	// TODO gotta be a better way of storing the key
	case class SshKeyTarget(host: String, port: Int,
							username: String, keyPath: String) extends Target {
		override def toString: String = s"SshKeyTarget($username@$host:$port(key=$keyPath))"
	}


	case class ServerStatus(version: String,
							nodes: Int, procedures: Int,
							load: Percentage, memory: ByteSize,
							startTime: ZonedDateTime)

	type TimeSeries[+A] = TreeMap[ZonedDateTime, A]

	case class TelemetrySeries(origin: Id[Node],
							   series: TimeSeries[Status] = TreeMap.empty[ZonedDateTime, Status])


	implicit val telemetrySeriesSemigroup: Semigroup[TelemetrySeries] = Semigroup.instance { (l, r) =>
		TelemetrySeries(r.origin, l.series ++ r.series)
	}


	case class LogSeries(origin: Id[Node],
						 series: TimeSeries[Seq[Line]] = TreeMap.empty[ZonedDateTime, Seq[Line]])

	implicit val logSeriesSemigroup: Semigroup[LogSeries] = Semigroup.instance { (l, r) =>
		LogSeries(r.origin, l.series ++ r.series)
	}

	case class Node(id: Id[Node],
					target: Target,
					remark: String,
					status: Option[Status] = None,
					logs: Map[Path, ByteSize] = Map(),
				   ) extends Entity[Node] {
		override def withId(a: Node, that: Id[Node]): Node = a.copy(id = that)
	}


	case class Procedure(id: Id[Procedure],
						 name: String,
						 remark: String,
						 code: String,
						 timeout: Duration,
						) extends Entity[Procedure] {
		override def withId(a: Procedure, that: Id[Procedure]): Procedure = a.copy(id = that)
	}


	//	sealed trait EventKind extends EnumEntry
	//	object EventKind extends enumeratum.Enum[EventKind] {
	//		case object PoolChanged extends EventKind
	//		case object NodeUpdated extends EventKind
	//		noinspection TypeAnnotation
	//		val values = findValues
	//	}

	sealed trait Event
	case class PoolChanged(pool: Set[Id[Node]]) extends Event
	case class NodeUpdated(delta: Set[Id[Node]]) extends Event

	sealed trait Outcome[+A]
	case class Altered[A](altered: Id[A]) extends Outcome[A]
	case class Failed(reason: String) extends Outcome[Nothing]

	implicit class EitherOutcomeInstance[+A](a: Either[Failure, Id[A]]) {
		def liftOutcome: Outcome[A] = a.fold(Failed, Altered(_))
	}
	ensureCodec[TreeMap[ZonedDateTime, String]]
	ensureCodec[Target]
	ensureCodec[Event]
	ensureCodec[ServerStatus]
	ensureCodec[LogSeries]
	ensureCodec[TelemetrySeries]
	ensureCodec[Node]
	ensureCodec[Procedure]


}
