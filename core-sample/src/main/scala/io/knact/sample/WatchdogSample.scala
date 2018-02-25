package io.knact.sample

import java.util.Random

import cats.implicits._
import io.knact._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.language.postfixOps

object WatchdogSample extends App {

	implicit val stringInstance: Connectable[String, Unit] = _ => Task.unit

	val subjects: Observable[Set[String]] =
		Observable.interval(10 seconds)
			.map { v => v + 5 }
			.map { v => v % 10 }
			.dump("Nodes")
			.map { v => List.tabulate(v.toInt) { i => s"u$i=$v" }.toSet }

	val watchdog = new Watchdog[String, Unit](subjects)

	val ok = Command[Unit, String] { _ =>
		Thread.sleep(new Random().nextInt(2000))
		Result.success("ok")
	}

	watchdog
		.dispatchRepeated(1 second, ok)
		.foreach { v => println(v) }

	Thread.sleep(Long.MaxValue)


}
