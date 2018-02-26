package io.knact.guard.server

import java.net.{SocketTimeoutException, UnknownHostException}
import java.time.ZonedDateTime

import com.google.common.base.Throwables
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Entity.{Node, SshKeyTarget, id}
import io.knact.guard.Telemetry.{DiskStat, MemoryStat, ThreadStat}
import io.knact.guard.{NodeRepository, Telemetry}
import io.knact.{Command, Connectable, Watchdog}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.Random

object MonkeyModule extends (NodeRepository => Task[Unit]) with LazyLogging {

	implicit val monkeyInstance: Connectable[Node, String] = (a: Node) => Task.pure(a.toString)

	override def apply(nodes: NodeRepository): Task[Unit] = Task {
		nodes.ids.subscribe()

		val wd = new Watchdog[Node, String](nodes.entities)
		val rand = new Random

		logger.info("Monkey started")
		Observable.interval(5 seconds).mapTask { tick =>
			Task.wanderUnordered((0 to 1).toList) { i =>
				nodes.insert(Node(
					id(1),
					SshKeyTarget(s"1_$i+$tick", 22, s"foo$i", Array(42)), "a"))
			}
		}.dump("Add").executeWithFork.subscribe()

		wd.dispatchRepeated(1 second, Command[String, Telemetry.Status] { _ =>
			Thread.sleep(rand.nextInt(1000))
			rand.nextInt(10) match {
				case 0 => Left(new UnknownHostException())
				case 1 => Left(new SocketTimeoutException())
				case 2 => Left(new ArrayIndexOutOfBoundsException)
				case _ => Right(Telemetry.Online(state = Telemetry.Ok,
					reason = Some(rand.nextString(5)),
					telemetry = Telemetry(
						uptime = java.time.Duration.ofSeconds(rand.nextInt(65536)),
						users = rand.nextInt(10),
						loadAverage = rand.nextDouble(),
						memoryStat = MemoryStat(
							total = 32352404,
							free = rand.nextInt(32352404),
							used = 234,
							cache = 4),
						threadStat = ThreadStat(
							running = rand.nextInt(100),
							sleeping = rand.nextInt(100),
							stopped = rand.nextInt(100),
							zombie = 4),
						diskStats = Map("/foo" -> DiskStat(rand.nextInt(1000), rand.nextInt(1000))))
				))
			}
		}).mapTask { case (id, node, r) =>
			//			logger.info(s"Writing $id $node $r")
			nodes.persist(node.id, ZonedDateTime.now(), r match {
				case Left(_: UnknownHostException)   => Telemetry.Offline
				case Left(_: SocketTimeoutException) => Telemetry.Timeout
				case Left(e)                         => Telemetry.Error(Throwables.getStackTraceAsString(e))
				case Right(value)                    => value
			})
		}.executeWithFork
			.subscribe()(Scheduler.forkJoin(
				name = "monkey",
				parallelism = sys.runtime.availableProcessors(),
				maxThreads = sys.runtime.availableProcessors()))

		()
	}
}
