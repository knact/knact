package io.knact.guard.server.service

import java.net.{SocketTimeoutException, UnknownHostException}
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import com.google.common.base.Throwables
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Entity.{Node, SshKeyTarget, id}
import io.knact.guard.Telemetry.{DiskStat, MemoryStat, NetStat, ThreadStat}
import io.knact.guard.server.Config
import io.knact.guard.server._
import io.knact.guard.{NodeRepository, Telemetry}
import io.knact.{Command, Connectable, Watchdog}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import squants.information._

import scala.concurrent.duration._
import scala.util.Random


object MonkeyService extends LazyLogging {

	implicit val monkeyInstance: Connectable[Node, String] = (a: Node) => Task.pure(a.toString)

	 def apply(config: Config, nodes: NodeRepository): Task[Unit] = Task {
		nodes.ids.subscribe()

		val wd = new Watchdog[Node, String](nodes.entities)
		val rand = ThreadLocalRandom.current()

		def randomIpAddress(): String = List.fill(4) {rand.nextInt(255)}.mkString(".")

		logger.info("Monkey started")
		Observable.interval(1 seconds).mapTask { tick =>
			Task.wanderUnordered((0 to rand.nextInt(10)).toList) { i =>
				nodes.insert(Node(
					id(1),
					SshKeyTarget(randomIpAddress(), i + tick.toInt, s"foo$i", "foo"), "a"))
			}
		}.dump("Add").executeWithFork.subscribe()

		Observable.interval(10 seconds).mapTask { tick =>
			for {
				ids <- nodes.list()
				removed <- Task.wanderUnordered(Random.shuffle(ids).take(rand.nextInt(10))) { i =>
					nodes.delete(i)
				}
			} yield removed
		}.dump("Remove").executeWithFork.subscribe()

		wd.dispatchRepeated(config.eventInterval, Command[String, Telemetry.Status] { _ =>
			Thread.sleep(rand.nextLong(10))
			rand.nextInt(10) match {
				case 0 => Left(new UnknownHostException())
				case 1 => Left(new SocketTimeoutException())
				case 2 => Left(new ArrayIndexOutOfBoundsException)
				case _ =>

					val totalRam = Gigabytes(2)
					val usedRam = Bytes(rand.nextInt(totalRam.toBytes.toInt / 2))
					val cachedRam = Bytes(rand.nextInt(totalRam.toBytes.toInt / 2))
					val freeRam = totalRam - (usedRam + cachedRam)

					val disks = (0 to rand.nextInt(5)).map {"sda" + _}.map { p =>
						val total = Terabytes(2)
						val used = Bytes(rand.nextLong(total.toBytes.toLong))
						p -> DiskStat(total - used, used)
					}.toMap


					val nets = (0 to rand.nextInt(5)).map { i =>
						s"inet$i" -> NetStat(
							inet = randomIpAddress(),
							mac = {
								val bytes6 = new Array[Byte](6)
								rand.nextBytes(bytes6)
								bytes6.map {"%02x".format(_)}.mkString(":")
							},
							bcast = "255.255.255.255",
							mask = "192.168.1.255",
							inet6 = None,
							scope = "LINK",
							tx = Megabytes(rand.nextInt(4096)),
							rx = Megabytes(rand.nextInt(4096)))
					}.toMap

					Right(Telemetry.Online(
						state = Telemetry.Ok,
						reason = Some(UUID.randomUUID.toString),
						telemetry = Telemetry(
							arch = "Foo",
							uptime = java.time.Duration.ofSeconds(rand.nextLong(65536)),
							users = rand.nextLong(10),
							processorCount = 4,
							loadAverage = rand.nextDouble(),
							memoryStat = MemoryStat(
								total = totalRam,
								free = freeRam,
								used = usedRam,
								cache = cachedRam),
							threadStat = ThreadStat(
								running = rand.nextLong(100),
								sleeping = rand.nextLong(100),
								stopped = rand.nextLong(100),
								zombie = 4),
							netStat = nets, diskStats = disks)
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
		}.executeAsync
			.subscribe()(Scheduler.forkJoin(
				name = "monkey",
				parallelism = sys.runtime.availableProcessors(),
				maxThreads = config.commandMaxThread))

		()
	}
}
