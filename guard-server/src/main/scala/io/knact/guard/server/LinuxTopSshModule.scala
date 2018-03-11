package io.knact.guard.server

import java.net.InetAddress
import java.time.ZonedDateTime

import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.ConsoleNode
import io.knact.Watchdog
import io.knact.guard.Entity.{Node, SshKeyTarget, SshPasswordTarget}
import io.knact.guard.NodeRepository
import io.knact.guard.Telemetry.Error
import io.knact.guard.server.Main.logger
import io.knact.linux.top
import io.knact.ssh.{PasswordCredential, PublicKeyCredential, SshAddress, SshAuth, SshCredential}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.concurrent.duration._

// TODO needs info about AWS
object LinuxTopSshModule extends (NodeRepository => Task[Unit]) with LazyLogging {

	implicit val targetSshAuthInstance: SshAuth[Node] = new SshAuth[Node] {
		override def address(a: Node): SshAddress = SshAddress(InetAddress.getByName(a.target.host), a.target.port)
		override def credential(a: Node): SshCredential = a.target match {
			case SshPasswordTarget(_, _, username, password) => PasswordCredential(username, password, ???)
			case SshKeyTarget(_, _, username, key)           => PublicKeyCredential(username, ???, ???)
		}
	}

	override def apply(nodes: NodeRepository): Task[Unit] = Task {
		val wd = new Watchdog[Node, ConsoleNode](nodes.entities)
		wd.dispatchRepeated(1 second, top.command)
			.doOnError(e => e.printStackTrace())
			.mapTask { case (id, node, r) =>
				logger.info(s"Writing $id $node $r")
				r match {
					case Left(e)     => nodes.persist(node.id, ZonedDateTime.now(), Error(e.getMessage))
					case Right(data) => nodes.persist(node.id, ZonedDateTime.now(), ???)
				}
			}.executeWithFork
			.subscribe()(Scheduler.forkJoin(
				name = "perf",
				parallelism = sys.runtime.availableProcessors(),
				maxThreads = sys.runtime.availableProcessors()))

		()
	}
}
