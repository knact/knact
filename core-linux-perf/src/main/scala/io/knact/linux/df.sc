import scala.io.Source


val i = Source.fromFile("/home/tom/knact/core-linux-perf/src/test/resources/df1.txt").mkString


case class DfEntry(fs: String, fstype: String, used: Long, avail: Long, mount: String)
case class DfData(entries: Seq[DfEntry])


val lines = i.lines.toSeq

//TODO so path can contain spaces
//TODO map the header's layout to all rows then trim

val headers = Seq("Used", "Avail", "Filesystem", "Mounted on", "Type")
val first = lines.head

// so we want"    Used    Avail Filesystem     Mounted on                    Type"
// to be mapped into a slice list: (0, 8), (9, 17) ...


headers.map { h => first.indexOf(h) }





