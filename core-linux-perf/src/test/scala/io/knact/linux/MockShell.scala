package io.knact.linux

import io.knact.Basic
import io.knact.Basic.MockConsoleTransport

import scala.io.Source

object MockShell {

	def mkMockedShell(source: Source): Basic.ConsoleNode = {
		new MockConsoleTransport(_ => source.buffered.mkString ).mkNode()
	}


}
