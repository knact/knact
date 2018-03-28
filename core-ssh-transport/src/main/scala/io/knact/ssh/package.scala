package io.knact

import cats.implicits._
import java.net.InetAddress
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.LazyLogging
import io.knact.Basic.{ConsoleIO, ConsoleNode}
import monix.eval.Task
import net.schmizz.sshj.SSHClient

package object ssh extends LazyLogging {

	sealed trait SshCredential {
		def username: String
		def hostPublicKey: Option[PublicKey]
		override def toString: String = s"$username[$hostPublicKey]"
	}

	case class PasswordCredential(username: String,
								  password: String,
								  hostPublicKey: Option[PublicKey] = None) extends SshCredential

	case class PublicKeyCredential(username: String,
								   publicKey: Path,
								   hostPublicKey: Option[PublicKey] = None) extends SshCredential


	case class SshAddress(host: InetAddress, port: Int)

	trait SshAuth[A] {
		def address(a: A): SshAddress
		def credential(a: A): SshCredential
	}

	def autoClosed[A](that: Command[ConsoleNode, A]) = Command[ConsoleNode, A] { n =>
		val result = Either.catchNonFatal {that.run(n)}.flatten
		n.unsafeTerminate()
		result
	}


	implicit def sshInstance[A](implicit ev: SshAuth[A]): Connectable[A, ConsoleNode] = (a: A) =>
		Task {

			val credential = ev.credential(a)
			val address = ev.address(a)

			val client = new SSHClient()
			logger.info(s"Connecting to $credential@$address")

			client.addHostKeyVerifier {
				// TODO is this even correct?
				(_: String, _: Int, that: PublicKey) => credential.hostPublicKey.forall(_ == that)
			}
			client.setConnectTimeout(10 * 1000)
			client.connect(address.host, address.port)
			credential match {
				case PasswordCredential(username, passphrase, _) =>
					client.authPassword(username, passphrase)
				case PublicKeyCredential(username, publicKey, _) =>
					client.authPublickey(username, publicKey.toAbsolutePath.toString)
			}

			val targetAddress = client.getRemoteAddress
			logger.info(s"SSH connection established on $targetAddress")
			new ConsoleNode {
				override def exec(command: String): ConsoleIO = {
					logger.info(s"\tAllocating session on $targetAddress...")
					val sshSession = client.startSession()
					logger.info(s"\t\tExecuting $command on $targetAddress")
					//					sshSession.allocateDefaultPTY()
					val chan = sshSession.exec(command)
					ConsoleIO(
						in = chan.getInputStream,
						err = chan.getErrorStream,
						out = chan.getOutputStream,
						isEof = () => chan.isEOF,
						close = () => chan.close())
				}
				override def unsafeTerminate(): Unit = {
					logger.info(s"Disconnecting from  $targetAddress")
					client.disconnect()
				}
			}
		}


}
