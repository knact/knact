package io.knact.guard.server

import java.net.InetAddress
import java.time.ZonedDateTime

import com.typesafe.scalalogging.LazyLogging
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import io.knact.Basic.ConsoleNode
import io.knact.Watchdog
import io.knact.guard.Telemetry.{Error, Offline, Online}
import io.knact.guard.Entity._
import io.knact.linux.top
import io.knact.ssh._
import monix.eval.Task
import io.knact.guard.server.scheduler
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService

import scala.concurrent.duration
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration._
//import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.http4s.server.blaze._

import scala.util.Try


object Main extends StreamApp[Task] with LazyLogging {


	case class Config(port: Int, eventInterval: FiniteDuration)

	implicit val targetSshAuthInstance: SshAuth[Node] = new SshAuth[Node] {
		override def address(a: Node): SshAddress = SshAddress(InetAddress.getByName(a.target.host), a.target.port)
		override def credential(a: Node): SshCredential = a.target match {
			case SshPasswordTarget(_, _, username, password) => PasswordCredential(username, password, ???)
			case SshKeyTarget(_, _, username, key)           => PublicKeyCredential(username, ???, ???)
		}
	}

	override def stream(args: List[String], requestShutdown: Task[Unit]): Stream[Task, ExitCode] = {
		val config = for {
			// TODO other parameters
			port <- Try {sys.env.getOrElse("port", "8080").toInt}.toEither
			eventInterval <- Try {
				Duration(sys.env.getOrElse("eventInterval", "1s")) match {
					case d: Infinite       =>
						throw new IllegalArgumentException(s"Infinite duration $d is unsupported")
					case d: FiniteDuration => d
				}
			}.toEither
		} yield Config(port, eventInterval)

		config match {
			case Left(e)             => Stream.raiseError(e)
			case Right(config @ Config(port, _)) =>
				val repos = new InMemoryContext(
					version = "0.0.1",
					startTime = ZonedDateTime.now())

				val service = new ApiService(repos, config)

				def migrate() = for {
					// TODO do actual migration and not just dummy data
					_ <- Task {logger.info("Migration started")}
					n <- repos.nodes.insert(Node(id(1), SshKeyTarget("1", 22, "foo", Array(42)), "a"))
					_ <- repos.nodes.insert(Node(id(1), SshKeyTarget("2", 22, "bar", Array(42)), "a"))
					_ <- repos.nodes.persist(n.right.get, ZonedDateTime.now(), Offline)
					_ <- Task {logger.info("Migration completed")}
				} yield ()

				def setupWatchdog() = Task {

					val ts: Observable[Set[Node]] =
						service.nodes.poolDelta.flatMap { ns =>
							Observable.fromTask(Task.traverse(ns)(service.nodes.find))
						}.map {_.flatten.toSet}

					ts.foreach { t => logger.info(s"Watchdog watching nodes $t ") }
					import scala.concurrent.duration._
					val wd = new Watchdog[Node, ConsoleNode](ts)
					wd.dispatchRepeated(1 second, top.command)
						.doOnError(e => e.printStackTrace())
						.foreach { case (id, node, r) =>
							logger.info(s"Writing $id $node $r")
							r match {
								case Left(e)     => service.nodes.persist(node.id, ZonedDateTime.now(), Error(e.getMessage))
								case Right(data) => service.nodes.persist(node.id, ZonedDateTime.now(), ???)
							}

						}

				}

				for {
					_ <- Stream.eval(migrate())
					_ <- Stream.eval(setupWatchdog())
					exit <- BlazeBuilder[Task]
						.bindHttp(port, "localhost")
						.mountService(service.services, "/api")
						.serve
				} yield exit
		}

	}
}