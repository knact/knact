package io.knact.guard

import java.time.Duration

import io.circe.{Decoder, Encoder}
import io.circe.java8.time._
import io.circe.generic.auto._
import io.knact.guard.Telemetry._
import shapeless.tag
import shapeless.tag.@@
import squants.UnitOfMeasure
import squants.information._
import squants.information.InformationConversions.InformationNumeric


case class Telemetry(arch: String,
					 uptime: Duration,
					 users: Long,
					 processorCount: Int,
					 loadAverage: Double,
					 cpuStat: CpuStat,
					 memoryStat: MemoryStat,
					 threadStat: ThreadStat,
					 netStat: Map[Iface, NetStat],
					 diskStats: Map[Path, DiskStat]) {
	def cpuPercent: Percentage = loadAverage / processorCount * 100.0
	def memPercent: Percentage = (memoryStat.used / memoryStat.total) * 100.0
	def diskPercent: Percentage = {
		val totalFree = diskStats.values.map {_.free}.sum
		val totalUsed = diskStats.values.map {_.used}.sum
		val total = totalFree + totalUsed
		if (total.toBytes == 0) 0
		else totalUsed / total * 100.0
	}
	def netTx: Information = netStat.values.map {_.tx}.sum
	def netRx: Information = netStat.values.map {_.rx}.sum


}

object Telemetry {

	// telemetry ADT

	type Iface = String


	final def InformationUnitsOfMeasures = Vector(
		Bytes, Kilobytes, Megabytes, Gigabytes, Terabytes,
		Petabytes, Exabytes, Zettabytes, Yottabytes)

	// XXX what about the IEC units?
	def findClosestUnit(information: Information): UnitOfMeasure[Information] = {
		if (information <= Bytes(0)) Bytes
		else InformationUnitsOfMeasures((Math.log10(information.toBytes) / Math.log10(1000)).toInt)
	}

	case class CpuStat(user: Percentage, system: Percentage) {
		def totalPercent: Percentage = user + system
	}
	case class MemoryStat(total: Information, free: Information, used: Information, cache: Information)
	case class ThreadStat(running: Long, sleeping: Long, stopped: Long, zombie: Long)
	case class NetStat(mac: String,
					   inet: String,
					   bcast: String,
					   mask: String,
					   inet6: Option[String],
					   scope: String, tx: Information, rx: Information) // tx/rx are cumulative not deltas
	case class DiskStat(free: Information, used: Information) {}


	sealed trait Verdict {override def toString = ""}
	case object Ok extends Verdict {override def toString: String = "Ok"}
	case object Warning extends Verdict {override def toString: String = "Warning"}
	case object Critical extends Verdict {override def toString: String = "Critical"}

	sealed trait Status
//	case class StatusGen(statMessage: String, error: String, verdict: String, reason: String) extends (Status) {
//		def toLegacy = (statMessage, error, verdict, reason)
//	}
	case object Offline extends Status {override def toString = "Offline"}
	case object Timeout extends Status {override def toString = "Timeout"}
	case class Error(error: String) extends Status
	case class Online(state: Verdict, reason: Option[String], telemetry: Telemetry) extends Status

	ensureCodec[Telemetry]
	ensureCodec[Verdict]
	ensureCodec[Status]

}
