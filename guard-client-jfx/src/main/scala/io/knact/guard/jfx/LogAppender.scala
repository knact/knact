package io.knact.guard.jfx

import org.slf4j.LoggerFactory
import java.io.{IOException, OutputStream}

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.{AppenderBase, OutputStreamAppender}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.{Instant, LocalDateTime}

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.StackTraceElementProxy
import io.knact.guard.jfx.LogAppender.LogEntry

class LogAppender(val f: LogEntry => Unit) extends AppenderBase[ILoggingEvent] {


	private val ctx : LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
	private val root: Logger        = ctx.getLogger("ROOT")

	root.setLevel(Level.INFO)
	root.addAppender(this)
	setContext(ctx)


	def detach(): Unit = {
		root.detachAppender(this)
	}

	override protected def append(event: ILoggingEvent): Unit = {
		if (!started) return

		val entry = LogEntry(Instant.ofEpochMilli(event.getTimeStamp),
			event.getLevel,
			event.getFormattedMessage,
			Option(event.getThrowableProxy)
				.map { proxy =>
					s"${proxy.getClassName}: ${proxy.getMessage}\n\t${
						proxy.getStackTraceElementProxyArray
							.map {_.getSTEAsString}.mkString("\n\t")
					}"
				})

		f(entry)
	}
}
object LogAppender {
	case class LogEntry(time: Instant,
						level: Level,
						message: String,
						exception: Option[String] = None)
}