package io.knact.guard.server.component

import java.lang.management.ManagementFactory

import javax.management._

import scala.collection.JavaConverters._
import scala.util.Try


object MxMetric {

	def processHeapMemory(): Long = ManagementFactory
		.getMemoryMXBean
		.getHeapMemoryUsage
		.getUsed

	def processCpuLoad(): Either[Throwable, Double] = {
		def mkError(msg: String) = Left(new Exception(msg))

		for {
			attrs <- Try {
				val mbs = ManagementFactory.getPlatformMBeanServer
				val name = ObjectName.getInstance("java.lang:type=OperatingSystem")
				mbs.getAttributes(name, Array[String]("ProcessCpuLoad")).asScala.toList
			}.toEither
			x <- attrs match {
				case (x: Attribute) :: _ => x.getValue match {
					case v: java.lang.Double => Right(v.toDouble)
					case e                   => mkError(s"Unexpected value $e")
				}
				case Nil                 => mkError("Attribute is empty")
				case bad                 => mkError(s"No pattern matches $bad")
			}
		} yield ((if (x < 0) 0 else x) * 1000) / 10.0
	}


}
