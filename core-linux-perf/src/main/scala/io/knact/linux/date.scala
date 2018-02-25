package io.knact.linux

import java.time.{OffsetDateTime, ZonedDateTime}

import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object date {

	final val command = Command[ConsoleNode, ZonedDateTime] { implicit n =>
		Try {
			OffsetDateTime.parse(sendAndReadUntilEOF("date --iso-8601=second").trim).toZonedDateTime
		}.toEither
	}

}
