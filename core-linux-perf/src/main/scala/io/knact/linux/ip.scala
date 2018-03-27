package io.knact.linux

import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object ip {

	final val command = Command[ConsoleNode, Int] { implicit n =>
		Try {sendAndReadUntilEOF("ip -stat").trim.toInt}.toEither
	}

}
