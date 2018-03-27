import scala.io.Source
//import fastparse.WhitespaceApi
//private val White = WhitespaceApi.Wrapper {
//	import fastparse.all._
//	NoTrace(" ".rep)
//}

val i = Source.fromFile("/home/tom/knact/core-linux-perf/src/test/resources/ip1.txt").mkString

Ip.p.parse(i)


sealed trait IpVersion
case object IPv4
case object IPv6

case class IpAddr(protocol: IpVersion, addr: String, mask: String, brd: String, scope: String)

case class IpIface(name: String, state: Seq[String], flags: String,
				   mac: String,
				   addrs: Seq[IpAddr])


case class IpAddrStat(ipIfaces: Seq[IpIface])

object Ip {

	import fastparse.all._

	private val ws       = " ".rep(1)
	private val lower    = 'a' to 'z'
	private val upper    = 'A' to 'Z'
	private val num      = '0' to '9'
	private val digit    = CharIn(num)
	private val alpha    = CharIn(lower, upper)
	private val alphaNum = CharIn(num, lower, upper)


	val p = Start ~ "\n".? ~
			digit ~ ":" ~ ws ~/
			alphaNum.rep(1).! ~ ":" ~ ws ~/
			"<" ~ CharIn(upper, "_").rep(1).!.rep(sep = ",") ~ ">" ~ ws ~/
			CharsWhile(_ != '\n').!

}






