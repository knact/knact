package io.knact

import java.io._
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{ExecutorService, Executors}

import monix.eval.Task

object Basic {

	case class ConsoleIO(in: InputStream,
						 err: InputStream,
						 out: OutputStream,
						 isEof: () => Boolean,
						 close: () => Unit)

	trait ConsoleNode {
		def exec(command: String): ConsoleIO
		def unsafeTerminate() : Unit
	}


	implicit def shInstance[A]: Connectable[A, ConsoleNode] = (_: A) => Task {
		new ConsoleNode {
			private val process: Process = Runtime.getRuntime.exec("/bin/sh")

			val s = ConsoleIO(
				in = process.getInputStream,
				err = process.getErrorStream,
				out = process.getOutputStream,
				isEof = process.isAlive,
				close = () => {process.destroy()})
			override def exec(command: String): ConsoleIO = ???
			override def unsafeTerminate(): Unit = ???
		}
	}


}
