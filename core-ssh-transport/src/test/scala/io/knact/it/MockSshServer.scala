package io.knact.it

import java.io.{InputStream, OutputStream, PipedInputStream, PipedOutputStream}
import java.security.{KeyPair, KeyPairGenerator}
import java.time.ZonedDateTime

import net.sf.expectit.filter.Filters.{removeColors, removeNonPrintable}
import net.sf.expectit.matcher.Matchers
import net.sf.expectit.{Expect, ExpectBuilder}
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider
import org.apache.sshd.server.{Environment, SshServer}
import org.apache.sshd.server.auth.hostbased.AcceptAllHostBasedAuthenticator
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.shell.{InvertedShell, InvertedShellWrapper}

class MockSshServer(val port: Int, f: String => String, keyPair: KeyPair) {

	val sshd: SshServer = SshServer.setUpDefaultServer()
	sshd.setPort(port)
	sshd.setKeyPairProvider(new MappedKeyPairProvider(keyPair))
	sshd.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE)
	sshd.setHostBasedAuthenticator(AcceptAllHostBasedAuthenticator.INSTANCE)
	sshd.setShellFactory { () =>
		new InvertedShellWrapper(new InvertedShell() {

			val out = new PipedInputStream()
			val err = new PipedInputStream()
			val in  = new PipedOutputStream()

			private val expect: Expect = new ExpectBuilder()
				.withInputs(new PipedInputStream(in))
				.withOutput(new PipedOutputStream(out))
				.withInputFilters(removeColors(), removeNonPrintable())
				.build()

			new Thread(() => {
				expect.interact().when(Matchers.contains("\n"))
					.`then`(v => {
						println(s"Recv ${						v.getBefore} ")
						expect.sendLine(f(v.getBefore))
					}).until(Matchers.eof())
			}).start()

			private var alive: Boolean = false
			override def exitValue(): Int = 0
			override def isAlive: Boolean = alive
			override def getInputStream: OutputStream = in
			override def getErrorStream: InputStream = err
			override def getOutputStream: InputStream = out
			override def setSession(session: ServerSession): Unit = {}
			override def start(env: Environment): Unit = {
				alive = true
			}
			override def destroy(): Unit = {
				alive = false
				Seq(out, err, in).foreach(_.close())
			}
		})
	}

	sshd.start()


}
