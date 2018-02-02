package io.knact.linux

import java.time.{OffsetDateTime, ZonedDateTime}

import fastparse.all.AnyChar
import fastparse.core.Parsed.{Failure, Success}
import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object env {

	import fastparse.all._

	private final val key = CharIn('a' to 'z', 'A' to 'Z', "_") ~
							CharIn('0' to '9', 'a' to 'z', 'A' to 'Z', "_").rep

	private final val parser = (key.! ~ "=" ~ (CharsWhile(_ != '\n') | "").!).rep(sep = "\n") ~/
							   (End | "\n")

	final val command = Command[ConsoleNode, Map[String, String]] { implicit n =>
		val str = sendAndReadUntilEOF("env")
		parser.parse(str) match {
			case Success(ts, _)     => Right(ts.toMap)
			case f@Failure(_, _, _) => Left(f.msg)
		}
	}

}
