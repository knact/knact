package io.knact.ssh

import java.security.PublicKey

import io.knact.Basic.{ConsoleIO, ConsoleNode, NetAddress}
import io.knact.{Subject, Transport}
import net.schmizz.sshj.SSHClient

class SshTransport extends Transport[NetAddress, SshCredential, ConsoleNode] {


	override def connect(subject: Subject[NetAddress, SshCredential]): ConsoleNode = {
		try {
			val client = new SSHClient()
			println(s"Connecting to ${subject.credential}")
			client.addHostKeyVerifier((hostname: String, port: Int, key: PublicKey) =>
				true)
			client.connect(subject.address.address, subject.address.port)
			subject.credential match {
				case PassphraseCredential(username, passphrase, _) =>
					client.authPassword(username, passphrase)
				case PublicKeyCredential(_, publicKey, _)          =>
					client.authPublickey(???)
			}

//			val shell = sshSession.startShell
//			val s = ConsoleIO(
//				in = shell.getInputStream,
//				err = shell.getErrorStream,
//				out = shell.getOutputStream,
//				close = { () =>
//					shell.close()
//					sshSession.close()
//					client.disconnect()
//				})
			println(s"SSH connection live ${client.getRemoteHostname}")
			new ConsoleNode {
				override def exec(command: String): ConsoleIO = {
					val sshSession = client.startSession()
//					sshSession.allocateDefaultPTY()
					val chan = sshSession.exec(command)
					ConsoleIO(
						in = chan.getInputStream,
						err = chan.getErrorStream,
						out = chan.getOutputStream,
						isEof = () => chan.isEOF,
						close = () => chan.close())				}
			}
		} catch {case e: Exception => throw new RuntimeException(e)}
	}
}
