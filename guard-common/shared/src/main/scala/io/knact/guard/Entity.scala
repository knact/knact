package io.knact.guard

import java.time.{Duration, ZonedDateTime}

import cats.Eq
import shapeless.tag
import shapeless.tag.@@
import io.circe.java8.time._
import io.circe.generic.auto._
import io.knact.guard.Entity.Id
import io.knact.guard.Telemetry.{Status, Verdict}

sealed trait Entity[A] {
	def id: Id[A]
	def withId(a: A, that: Id[A]): A
}

object Entity {

	// refined id
	type Id[A] = Long @@ A

	implicit def idEq[A]: Eq[Id[A]] = _ == _

	def id[T, B](id: B)(implicit ev: Numeric[B]): Id[T] = tag[T][Long](ev.toLong(id))

	sealed trait Target {
		def host: String
		def port: Int
	}
	case class SshPasswordTarget(host: String, port: Int,
								 username: String, password: String) extends Target
	case class SshKeyTarget(host: String, port: Int, username: String,
							key: Array[Byte]) extends Target


	case class ServerStatus(group: Long, nodes: Long, procedures: Long,
							startTime: ZonedDateTime, samples: Long)

	type TimeSeries[A] = Map[ZonedDateTime, A]

	case class TelemetrySeries(origin: Id[Node], series: TimeSeries[Status])
	case class LogSeries(origin: Id[Node], series: TimeSeries[Seq[Line]])


	case class Node(id: Id[Node],
					group: Id[Group],
					target: Target,
					remark: String,
					telemetries: TimeSeries[Option[Verdict]] = Map(),
					logs: Map[Path, ByteSize] = Map(),
				   ) extends Entity[Node] {
		override def withId(a: Node, that: Id[Node]): Node = a.copy(id = that)
	}


	case class Procedure(id: Id[Procedure],
						 description: String,
						 code: String,
						 timeout: Duration,
						) extends Entity[Procedure] {
		override def withId(a: Procedure, that: Id[Procedure]): Procedure = a.copy(id = that)
	}

	case class Group(id: Id[Group],
					 name: String,
					 nodes: Seq[Id[Node]],
					) extends Entity[Group] {
		override def withId(a: Group, that: Id[Group]): Group = a.copy(id = that)
	}

	sealed trait Outcome[+A]
	case class Altered[A](altered: Id[A]) extends Outcome[A]
	case class Failed(reason: String) extends Outcome[Nothing]

	implicit class EitherOutcomeInstance[+A](a: Either[Failure, Id[A]]) {
		def liftOutcome: Outcome[A] = a.fold(Failed, Altered(_))
	}

	ensureCodec[Target]
	ensureCodec[ServerStatus]
	ensureCodec[LogSeries]
	ensureCodec[TelemetrySeries]
	ensureCodec[Group]
	ensureCodec[Node]
	ensureCodec[Procedure]


}
