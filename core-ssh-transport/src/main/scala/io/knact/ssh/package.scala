package io.knact

import java.net.InetAddress
import java.security.PublicKey

import io.knact.Basic.{ConsoleIO, ConsoleNode}
import monix.eval.Task
import net.schmizz.sshj.SSHClient

package object ssh {

	sealed trait SshCredential {
		def username: String
		def hostPublicKey: PublicKey
		override def toString: String = s"$username[$hostPublicKey]"
	}

	case class PasswordCredential(username: String,
								  password: String,
								  hostPublicKey: PublicKey) extends SshCredential

	case class PublicKeyCredential(username: String,
								   publicKey: String,
								   hostPublicKey: PublicKey) extends SshCredential


	case class SshAddress(host: InetAddress, port: Int)

	trait SshAuth[A] {
		def address(a: A): SshAddress
		def credential(a: A): SshCredential
	}


	implicit def sshInstance[A](implicit ev: SshAuth[A]): Connectable[A, ConsoleNode] = (a: A) =>
		Task {
			try {

				val credential = ev.credential(a)
				val address = ev.address(a)

				val client = new SSHClient()
				println(s"Connecting to $credential")
				client.addHostKeyVerifier((hostname: String, port: Int, key: PublicKey) =>
					true)
				client.connect(address.host, address.port)
				credential match {
					case PasswordCredential(username, passphrase, _) =>
						client.authPassword(username, passphrase)
					case PublicKeyCredential(_, publicKey, _)        =>
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
							close = () => chan.close())
					}
				}
			} catch {case e: Exception => throw new RuntimeException(e)}
		}


}
