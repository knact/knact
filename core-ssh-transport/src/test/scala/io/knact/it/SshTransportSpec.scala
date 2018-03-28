package io.knact.it

import java.io._
import java.net.InetAddress
import java.nio.charset.{Charset, StandardCharsets}
import java.security.{KeyPair, KeyPairGenerator}
import java.time.{OffsetDateTime, ZonedDateTime}

import io.knact.Basic.ConsoleNode
import io.knact.Command
import io.knact.ssh.{PasswordCredential, PublicKeyCredential}
import io.knact.ssh._
import monix.reactive.Observable
import net.sf.expectit.{Expect, ExpectBuilder, Result}
import org.apache.sshd.common.Factory
import org.apache.sshd.server.session.ServerSession
import org.scalatest.{FlatSpec, Matchers}
import org.apache.sshd.server.{Environment, SshServer}
import org.apache.sshd.server.shell.{InvertedShell, InvertedShellWrapper, ProcessShellFactory}
import net.sf.expectit.filter.Filters.{removeColors, removeNonPrintable}
import net.sf.expectit.matcher.{Matchers => EMatchers}
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider
import org.apache.sshd.server.auth.hostbased.AcceptAllHostBasedAuthenticator
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import monix.execution.Scheduler.Implicits.global
import net.sf.expectit.interact.Action
import cats._
import cats.implicits._
import io.knact
import monix.eval.Task

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.sys.process.BasicIO


class SshTransportSpec extends FlatSpec with Matchers {

	behavior of "Command"


	final val Port = 0 // ephemeral port


	private val pk: KeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair()
	private val server      = new MockSshServer(
		port = Port,
		f = identity,
		keyPair = pk)


	// TODO setup(port, certs) needs to be more careful otherwise this becomes non-det
	// TODO still has ??? sprinkled throughout the impl
	ignore should "work" in {

		val s = sshInstance(new SshAuth[Unit] {
			override def address(a: Unit): SshAddress = SshAddress(InetAddress.getLocalHost, Port)
			override def credential(a: Unit): SshCredential = PasswordCredential("", "", None)
		})


		val echo = { s: String =>
			Command[ConsoleNode, String] { v =>
				val io = v.exec(s)
				val expect: Expect = new ExpectBuilder()
					.withInputs(io.in)
					.withOutput(io.out)
					.withInputFilters(removeColors(), removeNonPrintable())
					.build()
				Either.right(expect.send(s).expect(EMatchers.matches("\n")).getBefore)
			}
		}

		val ab = for {
			a <- echo("a")
			b <- echo("b")
		} yield a + b

		Await.result((for {
			n <- s.connect(())
			r <- Task {ab.run(n)}
		} yield r).runAsync, Duration.Inf) should be("ab")

	}

}
