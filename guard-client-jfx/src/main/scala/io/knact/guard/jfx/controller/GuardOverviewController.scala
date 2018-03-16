package io.knact.guard.jfx.controller

import java.time.{Instant, ZonedDateTime, Duration => JDuration}

import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Entity.{NodeUpdated, PoolChanged, ServerStatus}
import io.knact.guard.Found
import io.knact.guard.jfx.Model.AppContext
import io.knact.guard.jfx.RichScalaFX._
import io.knact.guard.jfx.Schedulers
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

import scala.concurrent.duration._
import scalafx.Includes._
import scalafx.scene.chart.XYChart.Series
import scalafx.scene.chart.{AreaChart, XYChart}
import scalafx.scene.layout.VBox
import scalafxml.core.macros.sfxml


@sfxml
class GuardOverviewController(private val root: VBox,
							  private val history: AreaChart[String, Double],
							  private val performance: AreaChart[String, Double],
							  private val context: AppContext) extends LazyLogging {

	private final val SeriesMaxLength = 200

	private val nodeCountSeries = new XYChart.Series[String, Double] {name = "Node count(pool)"}
	private val nodeDeltaSeries = new XYChart.Series[String, Double] {name = "Node delta(events)"}

	private val guardMemorySeries   = new XYChart.Series[String, Double] {name = "Memory(MB)"}
	private val guardCpuSeries      = new XYChart.Series[String, Double] {name = "CPU(%)"}
	private val guardResponseSeries = new XYChart.Series[String, Double] {name = "Response time(ms)"}


	val allSeries = Seq(nodeCountSeries, nodeDeltaSeries,
		guardMemorySeries, guardCpuSeries, guardResponseSeries)

	// TODO why is implicit conversion not working here???
	history.data = Seq(nodeCountSeries, nodeDeltaSeries).map {_.delegate}
	performance.data = Seq(guardMemorySeries, guardCpuSeries, guardResponseSeries).map {_.delegate}


	import java.time.format.DateTimeFormatter

	private final val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")


	private def appendSeries(series: Series[String, Double], x: ZonedDateTime, y: Double) = {
		val now = Instant.now
		series.getData += XYChart.Data(x.format(formatter), y, x)
		if (series.getData.size() > SeriesMaxLength) series.getData.remove(0)
	}


	// node pool + delta
	context.service.foreach {
		case None    =>
			allSeries.foreach { _.data = Nil }
		case Some(gs) =>
			gs.events.observeOn(Schedulers.JavaFx)
					.map {(ZonedDateTime.now, _)}
					.foreach {
						case (time, PoolChanged(pool))  => appendSeries(nodeCountSeries, time, pool.size)
						case (time, NodeUpdated(delta)) => appendSeries(nodeDeltaSeries, time, delta.size)
					}

	}


	private def timeTask[A](task: Task[A]): Task[(JDuration, A)] = {
		for {
			start <- Task.eval {Instant.now()}
			a <- task
			end <- Task.eval {Instant.now()}
		} yield (JDuration.between(start, end), a)
	}

	// cpu , mem, reponse time
	Observable.interval(1 second)
		.map { _ => context.service.value }
		.collect { case Some(gs) => gs }
		.mapTask { v => timeTask(v.status()) }
		.map { r => (ZonedDateTime.now, r) }
		.observeOn(Schedulers.JavaFx)
		.foreach {
			case (time, (duration, Found(ServerStatus(_, _, _, load, mem, _)))) =>
				appendSeries(guardResponseSeries, time, duration.toMillis)
				appendSeries(guardCpuSeries, time, load * 100.0)
				appendSeries(guardMemorySeries, time, mem * 1e-6)
			case (time, (duration, e))                                          =>
				appendSeries(guardResponseSeries, time, duration.toMillis)
				appendSeries(guardCpuSeries, time, 0)
				appendSeries(guardMemorySeries, time, 0)
				logger.error(s"Unable to get status for server, reason=$e")
		}


}
