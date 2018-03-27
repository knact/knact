package io.knact.linux

import java.time.{OffsetDateTime, ZonedDateTime}

import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object uname {

	final val command = Command[ConsoleNode, String] { implicit n =>
		Try {sendAndReadUntilEOF("uname -a").trim}.toEither
	}

}
