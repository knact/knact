package io.knact.sample


import io.knact.Basic.{ConsoleNode, NetAddress}
import io.knact.{Subject, Transport}
import io.knact.linux.{date, env, sleep}
import io.knact.ssh.{PassphraseCredential, SshCredential, SshTransport}
import cats._
import cats.implicits._


object SshTelemetriesSample extends App {


	val mt: Transport[NetAddress, SshCredential, ConsoleNode] = new SshTransport()

	val theSubject     = Subject(NetAddress.LocalHost(22), PassphraseCredential("foo", "bar", null))
	val x: ConsoleNode = mt.connect(theSubject)
	val komp           = for {
		date1 <- env.command
		_ <- sleep.command(2)
		date2 <- date.command
	} yield (date1, date2)
	println(komp.run(x))

}
