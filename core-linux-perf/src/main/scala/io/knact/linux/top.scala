package io.knact.linux

import java.time.{Duration, LocalTime}

import fastparse.core.Parsed.{Failure, Success}
import fastparse.{WhitespaceApi, all}
import io.knact.Basic.ConsoleNode
import io.knact.{Command, Result}

object top {


	case class TopData(summary: Summary,
					   tasks: ThreadStat,
					   cpu: CpuStat,
					   mem: MemStat,
					   swap: SwapStat,
					   procs: Seq[ProcStat])
	case class Summary(time: LocalTime, uptime: Duration, users: Long,
					   loadL1M: Double, loadL5M: Double, loadL15M: Double)
	case class CpuStat(user: Double, system: Double, nice: Double, idle: Double,
					   waitIO: Double, hardwareIRQ: Double, softwareIRQ: Double, steal: Double)
	case class ThreadStat(total: Long, running: Long, sleeping: Long, stopped: Long, zombie: Long)
	case class MemStat(total: Long, free: Long, used: Long, cache: Long)
	case class SwapStat(total: Long, free: Long, used: Long, availableMemory: Long)
	case class ProcStat(pid: Long,
						user: String,
						priority: Priority, nice: Long,
						virt: Long, res: Long, shr: Long,
						status: TopProcStatus,
						cpu: Double, memory: Double, time: Duration, command: String)

	sealed trait TopProcStatus
	case object USleep extends TopProcStatus
	case object Sleep extends TopProcStatus
	case object Running extends TopProcStatus
	case object Stopped extends TopProcStatus
	case object Zombie extends TopProcStatus
	case object MultiThreaded extends TopProcStatus

	sealed trait Priority
	case object RealTime extends Priority
	case class Ordinal(value: Long) extends Priority

	private object Alpha {

		import fastparse.all._

		val alpha = P(CharIn('a' to 'z', 'A' to 'Z', "_:"))
	}

	private object Num {

		import fastparse.all._

		private val digit = CharIn('0' to '9')
		val float: all.Parser[Double] = (digit.rep(1) ~ "." ~ digit.rep(1)).!.map { v => v.toDouble }.opaque("float")
		val long : all.Parser[Long]   = ("-".? ~ digit.rep(1)).!.map(_.toLong).opaque("long")
		val i2   : all.Parser[Int]    = digit.rep(min = 1, max = 2).!.map(_.toInt).opaque("i2")
	}

	private object Time {

		import Num._
		import fastparse.all._

		val hmsTime     = (i2 ~ ":" ~ i2 ~ ":" ~ i2).map { case (h, m, s) => LocalTime.of(h, m, s) }.opaque("hms")
		val hmsDuration = (long ~ ":" ~ i2 ~ "." ~ i2).map { case (h, m, s) =>
			Duration.ofHours(h.toLong).plusMinutes(m.toLong).plusSeconds(s.toLong)
		}.opaque("hmsD")
		val hhmm        = (i2 ~ ":" ~ i2).map { case (h, m) =>
			Duration.ofHours(h.toLong).plusMinutes(m.toLong)
		}.opaque("hhmm")
	}

	private val White = WhitespaceApi.Wrapper {
		import fastparse.all._
		NoTrace(" ".rep)
	}

	object TopParser {

		import Num._
		import Time._
		import fastparse.all._

		object Stats {

			import White._
			import fastparse.noApi._


			val day = (long ~ ("day" ~ "s".?)).map { d => Duration.ofDays(d) }
			val min = (long ~ "min" ~ "s".?).map { m => Duration.ofMinutes(m) }

			// top uses uptime format here which is stupid
			val upTime = "up" ~ (hhmm | min |
								 (day ~ "," ~ hhmm).map { case (d, hm) => d.plus(hm) } |
								 (day ~ "," ~ min).map { case (d, m) => d.plus(m) })

			val durationWithHm = (

				((long ~ ("day" ~ "s".?)).map { d => Duration.ofDays(d) } | (long ~ "min" ~ "s".?).map { m => Duration.ofMinutes(m) }) ~
				("," ~ hhmm).?).map { case (d, hm) => hm.map {d.plus}.getOrElse(d) }

			val summaryLine = ("top - " ~/ hmsTime ~/
							   upTime ~ "," ~/
							   (long ~ ("user" ~ "s".?)) ~ "," ~/
							   ("load average:" ~ float ~ "," ~ float ~ "," ~ float))
				.map { case (time, duration, users, (l1, l5, l15)) =>
					Summary(time, duration, users, l1, l5, l15)
				}
			val taskLine    = (("Tasks:" | "Threads:") ~ (long ~ "total" ~ ",") ~/
							   (long ~ "running" ~ ",") ~/
							   (long ~ "sleeping" ~ ",") ~/
							   (long ~ "stopped" ~ ",") ~/
							   (long ~ "zombie"))
				.map { case (total, running, sleeping, stopped, zombie) =>
					ThreadStat(total, running, sleeping, stopped, zombie)
				}
			val cpuLine     = ("%Cpu(s):" ~/
							   (float ~ "us" ~ ",") ~/
							   (float ~ "sy" ~ ",") ~/
							   (float ~ "ni" ~ ",") ~/
							   (float ~ "id" ~ ",") ~/
							   (float ~ "wa" ~ ",") ~/
							   (float ~ "hi" ~ ",") ~/
							   (float ~ "si" ~ ",") ~/
							   (float ~ "st"))
				.map { case (us, sy, ni, id, wa, hi, si, st) =>
					CpuStat(us, sy, ni, id, wa, hi, si, st)
				}
			val memLine     = ("KiB Mem" ~ ":" ~/
							   (long ~ "total" ~ ",") ~/
							   (long ~ "free" ~ ",") ~/
							   (long ~ "used" ~ ",") ~/
							   (long ~ "buff/cache"))
				.map { case (total, free, used, cache) => MemStat(total, free, used, cache) }
			val swapLine    = ("KiB Swap" ~ ":" ~/
							   (long ~ "total" ~ ",") ~/
							   (long ~ "free" ~ ",") ~/
							   (long ~ "used" ~ ".") ~/ // WTF
							   (long ~ "avail Mem"))
				.map { case (total, free, used, availMem) => SwapStat(total, free, used, availMem) }
		}

		private val ws  = " ".rep(1)
		private val wss = " ".rep

		object Procs {

			import fastparse.all._

			val dataHeader = wss ~ "PID" ~ ws ~ "USER" ~ ws ~
							 "PR" ~ ws ~ "NI" ~ ws ~
							 "VIRT" ~ ws ~ "RES" ~ ws ~ "SHR" ~ ws ~
							 "S" ~ ws ~
							 "%CPU" ~ ws ~
							 "%MEM" ~ ws ~
							 "TIME+" ~ ws ~ "COMMAND"

			private val hrs = (float ~ "t").map(v => (v * 1000000000).toLong) |
							  (float ~ "g").map(v => (v * 1000000).toLong) |
							  (float ~ "m").map(v => (v * 1000).toLong) |
							  long // k

			private val priority = "rt".!.map(_ => RealTime) |
								   long.map(Ordinal)

			private val status = "R".!.map { _ => Running } |
								 "S".!.map { _ => Sleep } |
								 "I".!.map { _ => MultiThreaded } |
								 "D".!.map { _ => USleep } |
								 "Z".!.map { _ => Zombie } |
								 "T".!.map { _ => Stopped }

			val dataRow = (wss ~
						   long ~ ws ~/ // PID
						   CharsWhile(_ != ' ').! ~ ws ~/ // USER
						   priority ~ ws ~/ // PR
						   long ~ ws ~/ // NI
						   hrs ~ ws ~/ // VIRT
						   hrs ~ ws ~/ // RES
						   hrs ~ ws ~/ // SHR
						   status ~ ws ~/ // STATUS
						   float ~ ws ~/ // CPU
						   float ~ ws ~/ // MEM
						   hmsDuration ~ ws ~/ // TIME+
						   CharsWhile(_ != '\n').!) // COMMAND
				.map { case (pid, user, priority, nice, virt, res, shr, status, cpu, memory, time, command) =>
				ProcStat(pid,
					user,
					priority,
					nice,
					virt,
					res,
					shr,
					status,
					cpu,
					memory,
					time,
					command)
			}
		}

		val body = (Stats.summaryLine ~ "\n" ~/ wss ~
					Stats.taskLine ~ "\n" ~/ wss ~
					Stats.cpuLine ~ "\n" ~/ wss ~
					Stats.memLine ~ "\n" ~/ wss ~
					Stats.swapLine ~ wss ~/
					"\n".rep(1) ~/ wss ~
					Procs.dataHeader ~ "\n" ~/ wss ~
					Procs.dataRow.rep(sep = "\n"))
			.map { case (summary, tasks, cpu, mem, swap, procs) =>
				TopData(summary, tasks, cpu, mem, swap, procs)
			}

		val parser = Start ~ body ~ (End | "\n".rep)

		val batchParser = (Start ~ (body ~ "\n".rep()).rep() ~ End).map { x => x.lastOption }

	}

	import TopParser._

	final val command = Command[ConsoleNode, TopData] { implicit n =>
		val str = sendAndReadUntilEOF("top -b -n2 -d0.5 -w512 -c")
		batchParser.parse(str) match {
			case Success(Some(v), _) => Result.success(v)
			case Success(None, _)    => Result.failure("batch capture failed with n=0")
			case f@Failure(_, _, _)  => Result.failure(f.msg)
		}
	}


}
