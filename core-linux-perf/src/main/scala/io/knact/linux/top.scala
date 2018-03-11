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
	case class Summary(time: LocalTime, uptime: Duration, users: Int,
					   loadL1M: Double, loadL5M: Double, loadL15M: Double)
	case class CpuStat(user: Double, system: Double, nice: Double, idle: Double,
					   waitIO: Double, hardwareIRQ: Double, softwareIRQ: Double, steal: Double)
	case class ThreadStat(total: Int, running: Int, sleeping: Int, stopped: Int, zombie: Int)
	case class MemStat(total: Long, free: Long, used: Long, cache: Long)
	case class SwapStat(total: Long, free: Long, used: Long, availableMemory: Long)
	case class ProcStat(pid: Int,
						user: String,
						priority: Priority, nice: Int,
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
	case class Ordinal(value: Int) extends Priority

	private object Alpha {

		import fastparse.all._

		val alpha = P(CharIn('a' to 'z', 'A' to 'Z', "_:"))
	}

	private object Num {

		import fastparse.all._

		private val digit = CharIn('0' to '9')
		val float  : all.Parser[Double] = (digit.rep(1) ~ "." ~ digit.rep(1)).!.map { v => v.toDouble }
		val integer: all.Parser[Int]    = ("-".? ~ digit.rep(1)).!.map(_.toInt)
		val i2     : all.Parser[Int]    = digit.rep(min = 1, max = 2).!.map(_.toInt)
	}

	private object Time {

		import Num._
		import fastparse.all._

		val hmsTime     = (i2 ~ ":" ~ i2 ~ ":" ~ i2).map { case (h, m, s) => LocalTime.of(h, m, s) }
		val hmsDuration = (integer ~ ":" ~ i2 ~ "." ~ i2).map { case (h, m, s) =>
			Duration.ofHours(h.toLong).plusMinutes(m.toLong).plusSeconds(s.toLong)
		}
		val hhmm        = (i2 ~ ":" ~ i2).map { case (h, m) =>
			Duration.ofHours(h.toLong).plusMinutes(m.toLong)
		}
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

			val daysWithHm  = ((integer ~ ("day" ~ "s".?)) ~ "," ~/ hhmm).map { case (d, hm) =>
				Duration.ofDays(d.toLong).plus(hm)
			}
			val summaryLine = ("top - " ~/ hmsTime ~/ ("up" ~ (hhmm | daysWithHm)) ~ "," ~/
							   (integer ~ ("user" ~ "s".?)) ~ "," ~/
							   ("load average:" ~ float ~ "," ~ float ~ "," ~ float))
				.map { case (time, duration, users, (l1, l5, l15)) =>
					Summary(time, duration, users, l1, l5, l15)
				}
			val taskLine    = (("Tasks:" | "Threads:") ~ (integer ~ "total" ~ ",") ~/
							   (integer ~ "running" ~ ",") ~/
							   (integer ~ "sleeping" ~ ",") ~/
							   (integer ~ "stopped" ~ ",") ~/
							   (integer ~ "zombie"))
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
							   (integer ~ "total" ~ ",") ~/
							   (integer ~ "free" ~ ",") ~/
							   (integer ~ "used" ~ ",") ~/
							   (integer ~ "buff/cache"))
				.map { case (total, free, used, cache) => MemStat(total, free, used, cache) }
			val swapLine    = ("KiB Swap" ~ ":" ~/
							   (integer ~ "total" ~ ",") ~/
							   (integer ~ "free" ~ ",") ~/
							   (integer ~ "used" ~ ".") ~/ // WTF
							   (integer ~ "avail Mem"))
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
							  integer.map {_.toLong} // k

			private val priority = "rt".!.map(_ => RealTime) |
								   integer.map(Ordinal)

			private val status = "R".!.map { _ => Running } |
								 "S".!.map { _ => Sleep } |
								 "I".!.map { _ => MultiThreaded } |
								 "D".!.map { _ => USleep } |
								 "Z".!.map { _ => Zombie } |
								 "T".!.map { _ => Stopped }

			val dataRow = (wss ~
						   integer ~ ws ~/ // PID
						   CharsWhile(_ != ' ').! ~ ws ~/ // USER
						   priority ~ ws ~/ // PR
						   integer ~ ws ~/ // NI
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

		val parser = (Start ~
					  Stats.summaryLine ~ "\n" ~/ wss ~
					  Stats.taskLine ~ "\n" ~/ wss ~
					  Stats.cpuLine ~ "\n" ~/ wss ~
					  Stats.memLine ~ "\n" ~/ wss ~
					  Stats.swapLine ~ wss ~/
					  "\n".rep(1) ~/ wss ~
					  Procs.dataHeader ~ "\n" ~/ wss ~
					  Procs.dataRow.rep(sep = "\n") ~
					  (End | "\n".rep))
			.map { case (summary, tasks, cpu, mem, swap, procs) =>
				TopData(summary, tasks, cpu, mem, swap, procs)
			}

	}

	import TopParser._

	final val command = Command[ConsoleNode, TopData] { implicit n =>
		val str = sendAndReadUntilEOF("top -b -n1 -w512 -c")
		parser.parse(str) match {
			case Success(v, _)      => Result.success(v)
			case f@Failure(_, _, _) => Result.failure(f.msg)
		}
	}


}
