package io.knact.guard

import io.circe.{Decoder, Encoder}
import monix.execution.Scheduler

import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.{FiniteDuration, _}

package object server {

	final val Version: String = "0.0.1"

	implicit def durationEncoderProof: Encoder[FiniteDuration] =
		Encoder.encodeString.contramap[FiniteDuration](_.toString)
	implicit def durationDecoderProof: Decoder[FiniteDuration] =
		Decoder.decodeString.emap { s =>
			Duration(s) match {
				case fd: FiniteDuration => Right(fd)
				case inf: Infinite      => Left(s"Duration $inf is infinite")
			}
		}

	case class Config(port: Int = 8088,
					  eventInterval: FiniteDuration = 1 second,
					  commandMaxThread: Int = sys.runtime.availableProcessors() * 10,
					  serverMaxThread: Int = sys.runtime.availableProcessors() * 2,
					 )

	implicit val scheduler: Scheduler = Scheduler.forkJoin(
		name = "guard-pre",
		parallelism = sys.runtime.availableProcessors(),
		maxThreads = sys.runtime.availableProcessors())

}
