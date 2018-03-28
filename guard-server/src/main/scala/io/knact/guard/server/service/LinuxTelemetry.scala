package io.knact.guard.server.service

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.ConsoleNode
import io.knact.{Command, Result}
import io.knact.guard.Telemetry
import io.knact.guard.Telemetry.{MemoryStat, ThreadStat}
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
			Telemetry.Online(
				state = Telemetry.Ok,
				reason = Some("TODO"),
				telemetry = telemetry
			)
		}

}
