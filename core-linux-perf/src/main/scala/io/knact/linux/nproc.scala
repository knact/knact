package io.knact.linux

import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object nproc {

	final val command = Command[ConsoleNode, Int] { implicit n =>
		Try {sendAndReadUntilEOF("nproc --all").trim.toInt}.toEither
	}

}
