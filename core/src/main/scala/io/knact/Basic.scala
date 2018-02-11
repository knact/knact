package io.knact

import java.io._
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{ExecutorService, Executors}

object Basic {


	case class PasswordCredential(userName: String, password: String) extends Credential {
		override def identity: String = userName
	}

	case class FileCredential(path: Path) extends Credential {
		override def identity: String = path.toString
	}

	case class NetAddress(address: InetAddress, port: Int) extends Address {
		override def name: String = address.toString
	}
	object NetAddress {
		final val LocalHost: Int => NetAddress = port => NetAddress(InetAddress.getLocalHost, port)
	}


	case class ConsoleIO(in: InputStream,
						 err: InputStream,
						 out: OutputStream,
						 isEof: () => Boolean,
						 close: () => Unit)

	trait ConsoleNode extends Node {def exec(command: String): ConsoleIO}

	class RuntimeShell extends Transport[Address, Credential, ConsoleNode] {

		def mkNode(): ConsoleNode = new ConsoleNode {
			private val process: Process = Runtime.getRuntime.exec("/bin/sh")

			val s = ConsoleIO(
				in = process.getInputStream,
				err = process.getErrorStream,
				out = process.getOutputStream,
				isEof = process.isAlive,
				close = () => {process.destroy()})
			override def exec(command: String): ConsoleIO = ???
		}
		override def connect(subject: Subject[Address, Credential]): ConsoleNode = mkNode()
	}


	class MockConsoleTransport[A <: Address, C <: Credential](f: String => String) extends Transport[A, C, ConsoleNode] {

		private val service: ExecutorService = Executors.newSingleThreadExecutor()

		override def connect(ignored: Subject[A, C]): ConsoleNode = mkNode()

		def mkNode(): ConsoleNode = (command: String) => {
			val _in = new PipedOutputStream()
			val _err = new PipedInputStream()
			val _out = new PipedInputStream()

			val in_ = new PipedInputStream(_in)
			val err_ = new ByteArrayInputStream(Array.empty)
			val out_ = new PipedOutputStream(_out)
			val io = ConsoleIO(
				in = in_,
				err = err_,
				out = out_,
				isEof = () => false,
				close = () => {
					_in.close()
					_out.close()
				})

			//			val expect: Expect = new ExpectBuilder()
			//				.withInputs(_out)
			//				.withOutput(_in)
			//				.withInputFilters(removeColors(), removeNonPrintable())
			//				.withExceptionOnFailure()
			//				.build()

			service.submit(new Runnable {
				override def run(): Unit = {
					val str = f(command)
					_in.write(str.getBytes(StandardCharsets.UTF_8))
					io.close()
				}
			})
			//				expect.interact().when(Matchers.contains("\n"))
			//					.`then`(v => {
			//						expect.send(f(v.getBefore))
			//					}).until(Matchers.eof())
			io
		}
	}

}
