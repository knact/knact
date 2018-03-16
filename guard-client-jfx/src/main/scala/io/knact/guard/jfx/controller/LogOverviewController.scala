package io.knact.guard.jfx.controller

import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Entity.{NodeUpdated, PoolChanged}
import io.knact.guard.jfx.LogAppender.LogEntry
import io.knact.guard.jfx.RichScalaFX._
import io.knact.guard.jfx.LogAppender
import io.knact.guard.jfx.Model.AppContext
import monix.execution.Scheduler.Implicits.global

import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.scene.control.{TableColumn, TableView}
import scalafxml.core.macros.sfxml

@sfxml
class LogOverviewController(private val entries: TableView[LogEntry],
							private val time: TableColumn[LogEntry, String],
							private val level: TableColumn[LogEntry, String],
							private val message: TableColumn[LogEntry, String],
							private val context: AppContext
						   ) extends LazyLogging {

	time.cellValueFactory = c => StringProperty(c.value.time.toString)
	level.cellValueFactory = c => StringProperty(c.value.level.toString)
	message.cellValueFactory = c => StringProperty(
		s"${c.value.message}${c.value.exception.fold("")(s => "\n" + s)}"
	)
	// prevent GC from killing the logger
	final val appender = new LogAppender({ entry => entry +=: entries.getItems })
	appender.start()

	logger.info("Logs redirected to view")

	context.service.foreach {
		case Some(value) =>
			logger.info(s"Log bound to server ${value.baseUri}")
			value.events.foreach {
				case PoolChanged(pool)  => logger.info(s"Pool changed, pool=$pool")
				case NodeUpdated(delta) => logger.info(s"Node updated, delta=$delta")
			}
		case None        =>
			logger.info("Log unbound from server")
	}

}
