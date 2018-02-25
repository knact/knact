package io.knact.linux

import java.io.{ByteArrayInputStream, PipedInputStream, PipedOutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ExecutorService, Executors}

import io.knact.Basic
import io.knact.Basic.{ConsoleIO, ConsoleNode}

import scala.io.Source

object MockShell {

	private val service: ExecutorService = Executors.newSingleThreadExecutor()

	def alwaysRespondWith(source: Source): Basic.ConsoleNode = {
		(_: String) => {
			val _in = new PipedOutputStream()
			val _err = new PipedInputStream()
			val _out = new PipedInputStream()

			val in_ = new PipedInputStream(_in)
			val err_ = new ByteArrayInputStream(Array.empty)
			val out_ = new PipedOutputStream(_out)
			val io = ConsoleIO(
				in = in_, err = err_, out = out_,
				isEof = () => false,
				close = () => {
					_in.close()
					_out.close()
				})

			service.submit(new Runnable {
				override def run(): Unit = {
					_in.write(source.buffered.mkString.getBytes(StandardCharsets.UTF_8))
					io.close()
				}
			})
			io
		}


	}
}
