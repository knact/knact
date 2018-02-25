package io.knact.linux

import fastparse.core.Parsed.{Failure, Success}
import io.knact.Basic.ConsoleNode
import io.knact.{Command, Result}

object env {

	import fastparse.all._

	private final val key = CharIn('a' to 'z', 'A' to 'Z', "_") ~
							CharIn('0' to '9', 'a' to 'z', 'A' to 'Z', "_").rep

	private final val parser = (key.! ~ "=" ~ (CharsWhile(_ != '\n') | "").!).rep(sep = "\n") ~/
							   (End | "\n")

	final val command = Command[ConsoleNode, Map[String, String]] { implicit n =>
		val str = sendAndReadUntilEOF("env")
		parser.parse(str) match {
			case Success(ts, _)     => Result.success(ts.toMap)
			case f@Failure(_, _, _) => Result.failure(f.msg)
		}
	}

}
