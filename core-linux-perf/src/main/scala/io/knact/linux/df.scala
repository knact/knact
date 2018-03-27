package io.knact.linux

import io.knact.Basic.ConsoleNode
import io.knact.Command

import scala.util.Try

object df {


	case class DfEntry(fs: String, fstype: String, used: Long, avail: Long, mount: String)
	case class DfData(entries: Seq[DfEntry])

	final val command = Command[ConsoleNode, DfData] { implicit n =>
		Try {
			val output = sendAndReadUntilEOF("df --output=used,avail,source,target,fstype").trim
			???
		}.toEither
	}

}
