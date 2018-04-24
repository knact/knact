import io.circe.{Decoder, _}
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.syntax._
import io.knact.guard.Entity.SshPasswordTarget

((25956 to 25995) ++ (52556 to 52595))
	.map { n => s"it0$n.users.bris.ac.uk" }
	.map { host => SshPasswordTarget(host, 22, "foo", "bar") }
	.map { xs => xs.asJson }.size