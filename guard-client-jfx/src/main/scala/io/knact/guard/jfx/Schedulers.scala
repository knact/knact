package io.knact.guard.jfx

import com.typesafe.scalalogging.LazyLogging
import monix.execution.Scheduler

import scala.concurrent.ExecutionContext
import scalafx.application.Platform

object Schedulers extends LazyLogging {

	val JavaFx: Scheduler = Scheduler(
		new ExecutionContext {
			override def execute(runnable: Runnable): Unit = {
				Platform.runLater(runnable)
			}
			override def reportFailure(t: Throwable): Unit = {
				t.printStackTrace()
				logger.error("Error on FX scheduler", t)
			}
		}
	)

	object Implicits {
		implicit val JavaFx: Scheduler = Schedulers.JavaFx
	}
}