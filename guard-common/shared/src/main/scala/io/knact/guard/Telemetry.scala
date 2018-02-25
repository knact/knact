package io.knact.guard

import java.time.Duration

import io.circe.java8.time._
import io.circe.generic.auto._
import io.knact.guard.Telemetry.{DiskStat, MemoryStat, ThreadStat}


case class Telemetry(uptime: Duration,
					 users: Long,
					 loadAverage: Double,
					 memoryStat: MemoryStat,
					 threadStat: ThreadStat,
					 diskStats: Map[Path, DiskStat])

object Telemetry {

	// telemetry ADT
	case class MemoryStat(total: Long, free: Long, used: Long, cache: Long)
	case class ThreadStat(running: Long, sleeping: Long, stopped: Long, zombie: Long)
	case class DiskStat(free: Long, used: Long)


	sealed trait Verdict
	case object Ok extends Verdict
	case object Warning extends Verdict
	case object Critical extends Verdict

	sealed trait Status
	case object Offline extends Status
	case object Timeout extends Status
	case class Error(error: String) extends Status
	case class Online(state: Verdict, reason: Option[String], telemetry: Telemetry) extends Status

	ensureCodec[Telemetry]
	ensureCodec[Verdict]
	ensureCodec[Status]

}
