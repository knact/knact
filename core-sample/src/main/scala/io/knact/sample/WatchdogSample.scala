package io.knact.sample

import java.util.Random

import cats.implicits._
import io.knact.{Connectable, _}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * A minimal core example of how to utilise the [[Watchdog]] class.
  * No real side effects exist in the sample, for a more complete use case see [[IncrementalDmesg]]
  */
object WatchdogSample extends App {

	// define evidence that we can map strings to a connectable
	implicit val stringInstance: Connectable[String, Unit] = _ => Task.unit

	// repeatedly create nodes via some interval
	val subjects: Observable[Set[String]] =
		Observable.interval(10 seconds)
			.map { v => v + 5 }
			.map { v => v % 10 }
			.dump("Nodes")
			.map { v => List.tabulate(v.toInt) { i => s"u$i=$v" }.toSet }

	// create our watchdog
	val watchdog = new Watchdog[String, Unit](subjects)

	// define a command that sleeps for some random amount of time then succeeds
	val ok = Command[Unit, String] { _ =>
		Thread.sleep(new Random().nextInt(2000))
		Result.success("ok")
	}

	// execute the command every second
	// notice that depending on which version of dispatchRepeat* you choose, the sequencing and the
	// time of the command execution would be different, see documentation of [[Watchdog]] for more
	// details
	watchdog
		//		.dispatchRepeatedSyncAll(1 second, ok)
		//		.dispatchRepeatedSyncInterval(1 second, ok)
		.dispatchRepeated(1 second, ok)
		.foreach { v => println(v) }

	// watchdog runs on a separate thread so we wait here
	Thread.sleep(Long.MaxValue)


}
