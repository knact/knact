package io.knact.guard.server.service

import java.io.IOException
import java.net.InetAddress
import java.time.ZonedDateTime

import better.files.File
import com.google.common.base.Throwables
import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.ConsoleNode
import io.knact.guard.Entity.{Node, SshKeyTarget, SshPasswordTarget}
import io.knact.guard.server.Config
import io.knact.guard.{NodeRepository, Telemetry}
import io.knact.ssh.{PasswordCredential, PublicKeyCredential, SshAddress, SshAuth, SshCredential, _}
import io.knact.{Watchdog, ssh}
import monix.eval.Task
import monix.execution.Scheduler

// TODO needs info about AWS
object LinuxSshPerfService extends LazyLogging {

	implicit val targetSshAuthInstance: SshAuth[Node] = new SshAuth[Node] {
		override def address(a: Node): SshAddress = SshAddress(InetAddress.getByName(a.target.host), a.target.port)
		override def credential(a: Node): SshCredential = a.target match {
			case SshPasswordTarget(_, _, username, password) => PasswordCredential(username, password)
			case SshKeyTarget(_, _, username, keyPath)       => PublicKeyCredential(username, File(keyPath).path)
		}
	}

	def apply(config: Config, nodes: NodeRepository): Task[Unit] = Task {
		val wd = new Watchdog[Node, ConsoleNode](nodes.entities)
		wd.dispatchRepeated(config.eventInterval, ssh.autoClosed(LinuxTelemetry.command)).dump("A")
			.doOnError(e => e.printStackTrace())
			.mapTask { case (id, node, r) =>
				logger.info(s"Writing $id $node $r")
				nodes.persist(node.id, ZonedDateTime.now(),
					r match {
						case Left(e: IOException) =>
							e.printStackTrace()
							Telemetry.Error(Throwables.getStackTraceAsString(e))
						case Left(e)              => Telemetry.Error(Throwables.getStackTraceAsString(e))
						case Right(data)          => data
					}
				)
			}.executeAsync
			.subscribe()(Scheduler.forkJoin(
				name = "perf-ssh",
				parallelism = sys.runtime.availableProcessors(),
				maxThreads = config.commandMaxThread))

		()
	}.onErrorHandle(_.printStackTrace())

}
