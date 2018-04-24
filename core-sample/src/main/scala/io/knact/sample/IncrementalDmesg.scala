package io.knact.sample

import java.net.{Inet4Address, InetAddress}
import java.time.ZonedDateTime

import cats.data.Kleisli
import cats.effect.Effect
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.ConsoleNode
import io.knact.linux.{date, sendAndReadUntilEOF}
import io.knact.{Command, Connectable, Result, Watchdog}
import io.knact.ssh.{PasswordCredential, SshAddress, SshAuth, SshCredential}
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global
import io.knact.ssh._
import monix.eval.Task
import monix.reactive.subjects.ConcurrentSubject

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * A more complicated example detailing connection with a ssh enabled computer.
  * This sample contains real side effects of connecting to a ssh server and executing multiple
  * commands composed using [[cats.data.Kleisli]](simplified via [[io.knact.Command]])
  */
object IncrementalDmesg extends App with LazyLogging {


	// check that we have the correct monad that suspends effects
	// we only support monix Task for now
	implicitly[Effect[Task]]

	// define target model used to describe something that can be connected
	// in this case, out target is computer with ssh enabled
	case class MyNode(address: SshAddress, credential: SshCredential)

	// define an evidence that allows transformation from our model to a SshAuth[A]
	implicit val target: SshAuth[MyNode] = new SshAuth[MyNode] {
		override def address(a: MyNode): SshAddress = a.address
		override def credential(a: MyNode): SshCredential = a.credential
	}

	// check that evidence is provided for a connectable to be formed
	implicitly[Connectable[MyNode, ConsoleNode]]

	// define a stateful subject to write to later
	val subject: ConcurrentSubject[Set[MyNode], Set[MyNode]] = ConcurrentSubject.publish

	// create our watchdog
	val watchdog: Watchdog[MyNode, ConsoleNode] = new Watchdog(subject)

	// define the resulting type, i.e what we want to get back
	case class DmesgFragment(start: ZonedDateTime, fragment: String, end: ZonedDateTime)

	// define a dmesg command that collects dmesg via standard IO
	val dmesg: Command[ConsoleNode, String] = Command[ConsoleNode, String] {
		implicit n => Result.success(sendAndReadUntilEOF("dmesg -P"))
	}

	// compose multiple commands together, here we time the duration of the dmesg command by
	// sandwiching it between two date commands
	val fragmentCommand: Command[ConsoleNode, DmesgFragment] = for {
		start <- date.command
		fragment <- dmesg
		end <- date.command
	} yield DmesgFragment(start, fragment, end)
	// one can of course opt to write everything in one command for performance reasons



	// repeat the command defined above every second
	watchdog.dispatchRepeated(1 second, fragmentCommand)
		.doOnError(_.printStackTrace()) // log any error to stdio
		.foreach {
		// print out the content for debugging
		f => println(f)
	}

	// finally, supply our subject, this kick starts the watchdog
	// this can happen on another thread and the watchdog would adjust accordingly
	subject.onNext(Set(
		// replace with actual credentials
		MyNode(SshAddress(InetAddress.getLocalHost, 22), PasswordCredential("<username>", "<password>"))
	))

	// watchdog runs on a separate thread so we wait here
	Thread.sleep(Long.MaxValue)

}
