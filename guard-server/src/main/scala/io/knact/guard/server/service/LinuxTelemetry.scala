package io.knact.guard.server.service

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.ConsoleNode
import io.knact.{Command, Result}
import io.knact.guard.Telemetry
import io.knact.guard.Telemetry.{CpuStat, MemoryStat, ThreadStat, Verdict}
import io.knact.linux.top.TopData
import io.knact.linux.{env, nproc, top, uname}
import squants.information.Bytes

object LinuxTelemetry extends LazyLogging {

	final val command: Command[ConsoleNode, Telemetry.Online] =
		for {
			arch <- uname.command
			ncpu <- nproc.command
			envs <- env.command
			top <- top.command
		} yield {


			val TopData(summary, tasks, cpu, mem, swap, procs) = top


			val telemetry = Telemetry(
				arch = arch,
				uptime = summary.uptime,
				users = summary.users,
				processorCount = ncpu,
				loadAverage = summary.loadL1M,
				cpuStat = CpuStat(
					user = cpu.user,
					system = cpu.system),
				memoryStat = MemoryStat(
					total = Bytes(mem.total),
					free = Bytes(mem.free),
					used = Bytes(mem.used),
					cache = Bytes(mem.cache)
				),
				threadStat = ThreadStat(
					running = tasks.running,
					sleeping = tasks.sleeping,
					stopped = tasks.stopped,
					zombie = tasks.zombie
				),
				netStat = Map(),
				diskStats = Map())


			val freeRamPercentage = telemetry.memoryStat.free / telemetry.memoryStat.total
			val (state, reason) =
				if (freeRamPercentage < 0.3) {
					(Telemetry.Warning, Some("Less than 30% RAM"))
				} else if (freeRamPercentage < 0.07) {
					(Telemetry.Critical, Some("Less than 7% RAM"))
				} else if (telemetry.cpuStat.totalPercent > 75) {
					(Telemetry.Warning, Some("high CPU load"))
				} else if (telemetry.cpuStat.totalPercent < 10) {
					(Telemetry.Critical, Some("unexpected CPU idle"))
				} else (Telemetry.Ok, None)

			Telemetry.Online(
				state = state,
				reason = reason,
				telemetry = telemetry
			)
		}

}
