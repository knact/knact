package io.knact.guard
import java.time.{Duration, ZonedDateTime}

import io.knact.Basic.{NetAddress, PasswordCredential}
import io.knact.{Address, Command, Credential, Subject}
import monix.eval.Task

package object guard {




	case class EntityId(id: Long) extends AnyVal


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

	// common root
	sealed trait Entity {def id: EntityId}

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
	case class ProcedureView(id: EntityId, description: String, timeout: Duration)
	case class Procedure(id: EntityId,
						 description: String,
						 code: String,
						 timeout: Duration) extends Entity

	type TimeSeries[A] = Map[ZonedDateTime, A]
	type TelemetrySeries = TimeSeries[Either[Failure, Snapshot]]
	type LogSeries = TimeSeries[Seq[Line]] // time series of log tails

	case class NodeMeta(id: EntityId,
						telemetries: TelemetrySeries,
						logs: Map[Path, ByteSize])

	case class Node(id: EntityId,
					subject: Subject[NetAddress, PasswordCredential],
					remark: String) extends Entity


	case class GroupView(id: EntityId, name: String, nodes: Seq[EntityId])
	case class Group(id: EntityId,
					 name: String,
					 nodes: Seq[Node],
					 procedures: Seq[Procedure]) extends Entity


	trait Repository[A] {
		def findAll(): Task[Seq[A]]
		def findById(id: EntityId): Task[Option[A]]
		def upsert(group: A): Task[Either[Failure, A]]
		def delete(id: EntityId): Task[Either[Failure, EntityId]]
	}

	trait GroupRepo extends Repository[Group]

	trait ProcedureRepo extends Repository[Procedure]
	trait NodeRepo extends Repository[Node] {
		def telemetries(id: EntityId)(bound: Bound): Task[TelemetrySeries]
		def logs(id: EntityId)(path: Path)(bound: Bound): Task[LogSeries]
		def execute(id: EntityId)(procedureId: EntityId): Task[String]

	}

}
