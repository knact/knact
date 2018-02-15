package io.knact

import java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME
import java.time.{Duration, ZonedDateTime}

import io.circe._
import io.knact.Basic.{NetAddress, PasswordCredential}
import monix.eval.Task
import shapeless.tag
import shapeless.tag.@@

import scala.util.Try

package object guard {


	// XXX we are using tagged refinements, which is not supported
	// wanted : taggedEncoderProof[T, U](implicit ev : Encoder[T]) : Encoder[T @@ U]=???
	// that causes ev to diverge, if the solver had SAT or something maybe it would work...
	// see https://github.com/circe/circe/issues/220
	implicit def taggedLongEncoderProof[U]: Encoder[Long @@ U] = {
		import cats.syntax.contravariant._
		Encoder[Long].narrow
	}
	implicit def taggedLongDecoderProof[U]: Decoder[Long @@ U] = {
		Decoder[Long].map { l => tag[U][Long](l) }
	}


	implicit def zdtKeyEncoder: KeyEncoder[ZonedDateTime] =
		key => key.format(ISO_ZONED_DATE_TIME)

	implicit def zdtKeyDecoder: KeyDecoder[ZonedDateTime] =
		key => Try {ZonedDateTime.parse(key)}.toOption


	type Line = String
	type Path = String
	type ByteSize = Long
	type Failure = String


	// bounds and parameters
	sealed trait Sort
	case object Asc extends Sort
	case object Desc extends Sort

	case class Parameters(limit: Int, offset: Int, sort: Sort)
	case class Bound(start: Option[ZonedDateTime],
					 end: Option[ZonedDateTime])

	// refined id
	type Id[A] = Long @@ A

	sealed trait Entity[A] {
		def id: Id[A]
		//		implicit val idAEnc: Encoder[Id[A]] = deriveEncoder
		//		implicit val idADec: Decoder[Id[A]] = deriveDecoder
	}
	object Entity {def id[T](id: Long): Id[T] = tag[T][Long](id)}

	// telemetry ADT
	case class MemoryStat(total: Long, free: Long, used: Long, cache: Long)
	case class ThreadStat(running: Long, sleeping: Long, stopped: Long, zombie: Long)
	case class DiskStat(free: Long, used: Long)
	case class Snapshot(uptime: Duration,
						users: Long,
						loadAverage: Double,
						memoryStat: MemoryStat,
						threadStat: ThreadStat,
						diskStats: Map[Path, DiskStat])


	// stored procedure
	case class ProcedureView(id: Id[Procedure],
							 description: String, timeout: Duration)
	case class Procedure(id: Id[Procedure],
						 description: String,
						 code: String,
						 timeout: Duration) extends Entity[Procedure]

	type TimeSeries[A] = Map[ZonedDateTime, A]
	type TelemetrySeries = TimeSeries[Either[Failure, Snapshot]]
	type LogSeries = TimeSeries[Seq[Line]] // time series of log tails


	case class Node(id: Id[Node],
//					subject: Subject[NetAddress, PasswordCredential],
					telemetries: TelemetrySeries,
									logs : Map[Path, LogSeries]
					) extends Entity[Node]

	case class TelemetrySummary(failed: Long, success: Long)
	case class NodeView(id: Id[Node],
						subject: Subject[NetAddress, PasswordCredential],
											telemetry : TelemetrySummary,
											logs: Map[Path, ByteSize],
						remark: String) extends Entity[Node]

	case class GroupView(id: Id[Group],
						 name: String, nodes: Seq[Id[Node]])
	case class Group(id: Id[Group],
					 name: String,
					 nodes: Seq[Node],
					 procedures: Seq[Procedure]) extends Entity[Group]


	trait Repository[A <: Entity[A], V, F[_]] {
		def mapToView(a: A): V
		def findAll(): F[Seq[A]]
		def findById(id: Id[A]): F[Option[A]]
		def upsert(group: A): F[Either[Failure, A]]
		def delete(id: Id[A]): F[Either[Failure, Id[A]]]
		def deleteAll() : F[Either[Failure, Unit]]
		def tag(id: Long): Id[A] = Entity.id[A](id)
	}

	trait GroupRepo extends Repository[Group, GroupView, Task]
	trait ProcedureRepo extends Repository[Procedure, ProcedureView, Task]
	trait NodeRepo extends Repository[Node, NodeView, Task] {
		def telemetries(id: Id[Node])(bound: Bound): Task[Option[TelemetrySeries]] //changed that to Task[Option[TelemetrySeries]]
		def logs(id: Id[Node])(path: Path)(bound: Bound): Task[Option[LogSeries]] //and that
		def execute(id: Id[Node])(procedureId: Id[Procedure]): Task[Option[String]]	//and that
	}

}
