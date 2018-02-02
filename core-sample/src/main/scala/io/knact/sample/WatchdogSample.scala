package io.knact.sample

import java.util.Random

import io.knact.Basic._
import io.knact._
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.language.postfixOps

object WatchdogSample extends App {


	val ok = Command[ConsoleNode, String] { _ =>
		Thread.sleep(new Random().nextInt(2000))
		Result.success("ok")
	}

	val subjects: Observable[Set[Subject[Address, Credential]]] =
		Observable.interval(10 seconds)
			.map { v => v + 5 }
			.map { v => v % 10 }
			.dump("Nodes")
			.map { v =>
				List.tabulate(v.toInt) { i =>
					Subject(NetAddress.LocalHost(22), PasswordCredential(s"u$i=$v", ""))
				}.toSet
			}

	val watchdog = new Watchdog[Address, Credential, ConsoleNode](
		subjects = subjects,
		transport = (_: Subject[Address, Credential]) => (_: String) => ???)

	watchdog
		.dispatchRepeated(1 second, ok)
		.foreach { v => println(v) }

	Thread.sleep(Long.MaxValue)


}
