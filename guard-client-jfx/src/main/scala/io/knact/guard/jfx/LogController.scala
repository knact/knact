package io.knact.guard.jfx

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.jfx.LogAppender.LogEntry
import org.slf4j.event.Level

import scalafx.scene.control.{ListView, TableColumn, TableView}
import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafxml.core.macros.sfxml


@sfxml
class LogController(val entries: TableView[LogEntry],
					val time: TableColumn[LogEntry, String],
					val level: TableColumn[LogEntry, String],
					val message: TableColumn[LogEntry, String],
				   ) extends LazyLogging {

	time.cellValueFactory = c => StringProperty(c.value.time.toString)
	level.cellValueFactory = c => StringProperty(c.value.level.toString)
	message.cellValueFactory = c => StringProperty(
		s"${c.value.message}${c.value.exception.fold("")(s => "\n" + s)}"
	)

	val x = new LogAppender({ entry =>
		println(s">> $entry << ")
		entries.getItems += entry
	})
	x.start()

	logger.info("Logs redirected to view")

}
