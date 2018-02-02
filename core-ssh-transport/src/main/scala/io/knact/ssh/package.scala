package io.knact

import java.security.PublicKey

package object ssh {


	sealed trait SshCredential extends Credential {
		def username: String
		def hostPublicKey: PublicKey
		override def identity: String = s"$username[$hostPublicKey]"
	}

	case class PassphraseCredential(username: String,
									passphrase: String,
									hostPublicKey: PublicKey) extends SshCredential


	case class PublicKeyCredential(username: String,
								   publicKey: String,
								   hostPublicKey: PublicKey) extends SshCredential

}
