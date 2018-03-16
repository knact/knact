package io.knact.guard.jfx.controller

import java.time
import java.time.ZonedDateTime

import cats.data.EitherT
import io.knact.guard
import io.knact.guard.{Bound, ClientError, ConnectionError, DecodeError, Entity, Found, NotFound, Path, Percentage, ServerError, Telemetry}
import io.knact.guard.Entity.{Id, Node, NodeUpdated, TelemetrySeries, TimeSeries}
import io.knact.guard.Telemetry._
import io.knact.guard.jfx.Model.{AppContext, NodeError, NodeHistory, NodeItem, StatusEntry}
import io.knact.guard.jfx.RichScalaFX._
import io.knact.guard.jfx.Schedulers
import monix.eval.Task
import monix.reactive.Observable
import scalafx.scene.chart._
import scalafx.scene.control._
import scalafx.Includes._
import scalafx.scene.layout.{HBox, StackPane}
import scalafxml.core.macros.sfxml
import monix.execution.Scheduler.Implicits.global
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.cell.ProgressBarTableCell

@sfxml
class NodeDetailController(private val root: StackPane,
						   private val nodeInfo: Label,
						   private val systemStatus: StackedBarChart[String, Double],
						   private val diskStatus: TableView[(Path, DiskStat)],
						   private val diskPath: TableColumn[(Path, DiskStat), Path],
						   private val diskUsed: TableColumn[(Path, DiskStat), Percentage],
						   private val networkStatus: TableView[(Iface, NetStat)],
						   private val networkIface: TableColumn[(Iface, NetStat), Iface],
						   private val networkStat: TableColumn[(Iface, NetStat), String],
						   private val performanceSeries: AreaChart[String, Number],
						   private val memorySeries: AreaChart[String, Number],
						   private val diskSeries: AreaChart[String, Number],
						   private val networkSeries: AreaChart[String, Number],
						   private val logPaths: TableView[Telemetry],
						   private val logPane: StackPane,
						   private val infoArch: Label,
						   private val infoProcessors: Label,
						   private val infoUptime: Label,
						   private val infoUsers: Label,
						   private val infoRemark: Label,
						   private val infoIfacePane: HBox,
						   private val analyticTable: TableView[StatusEntry],
						   private val analyticTime: TableColumn[StatusEntry, ZonedDateTime],
						   private val analyticEvent: TableColumn[StatusEntry, String],
						   private val analyticReason: TableColumn[StatusEntry, String],
						   private val modal: Label,

						   private val node: NodeItem,
						   private val context: AppContext,
						  ) {


	private def showModal(s: String): Unit = {
		modal.visible = true
		modal.text = s
		println(s)
	}


	def updateStatus(status: Status) = {

		val statViews = Seq(diskStatus, networkStatus, systemStatus)

		status match {
			case Online(_, _, telemetry) =>
				statViews.foreach {_.disable = false}


				val cpuSeries = new XYChart.Series[String, Double] {name = "CPU"}


				val usedSeries = new XYChart.Series[String, Double] {name = "Used"}
				val cacheSeries = new XYChart.Series[String, Double] {name = "Cache"}
				val freeSeries = new XYChart.Series[String, Double] {name = "Free"}
				systemStatus.data = Seq(cpuSeries, usedSeries, cacheSeries, freeSeries).map {_.delegate}

				cpuSeries.data = Seq(XYChart.Data(cpuSeries.name.value, telemetry.cpuPercent))

				val mstat = telemetry.memoryStat
				usedSeries.data = Seq(XYChart.Data("Memory", mstat.used / mstat.total))
				cacheSeries.data = Seq(XYChart.Data("Memory", mstat.cache / mstat.total))
				freeSeries.data = Seq(XYChart.Data("Memory", mstat.free / mstat.total))

				diskStatus.items = ObservableBuffer(telemetry.diskStats.toSeq)
				diskPath.cellValueFactory = { v => StringProperty(v.value._1) }
				diskUsed.cellValueFactory = { v => ObjectProperty(v.value._2.free / v.value._2.used) }
				diskUsed.cellFactory = { _ =>
					new TableCell[(Path, DiskStat), Percentage] {
						item.onChangeOption {
							case None    => graphic = null
							case Some(p) => graphic = new HBox {
								children = Seq(
									new Label(f"${p * 100}%.1f%%"),
									new ProgressBar() {
										maxWidth = Double.MaxValue
										progress = p
									}
								)
							}
						}
					}
				}

				networkStatus.items = ObservableBuffer(telemetry.netStat.toSeq)
				networkIface.cellValueFactory = { v => StringProperty(v.value._1) }
				networkStat.cellValueFactory = { v =>
					val stat = v.value._2
					ObjectProperty(s"Up:${stat.tx.toString(findClosestUnit(stat.tx))}" +
								   s"|Down:${stat.rx.toString(findClosestUnit(stat.rx))}")
				}


				infoArch.text = telemetry.arch
				infoProcessors.text = telemetry.processorCount.toString
				infoUptime.text = telemetry.uptime.toString
				infoUsers.text = telemetry.users.toString

				infoIfacePane.children = telemetry.netStat.map {
					case (iface, NetStat(mac, inet, bcast, mask, inet6, scope, _, _)) =>
						s"""$iface
						   |	MAC   :$mac
						   |	Inet  :$inet
						   |	Bcast :$bcast
						   |	Mask  :$mask
						   |	Inet6 :${inet6.getOrElse("N/A")}
						   |	Scope :$scope
						   | """.stripMargin
				}.map {new Label(_)}

			case _ =>
				statViews.foreach {_.disable = true}
		}


	}

	def updateHistorySeries(series: TimeSeries[Status]) = {}
	def updateLogs(series: TimeSeries[Status]) = {}
	def updateAnalytic(series: TimeSeries[Status]) = {
		val entries = series.map { case (time, s) =>
			s match {
				// TODO verdict needs to be an enum, printing the class name is just stupid
				case Online(state, reason, _) => StatusEntry(time, state.toString, reason.getOrElse(" - "))
				case Offline                  => StatusEntry(time, "Offline", "N/A")
				case Timeout                  => StatusEntry(time, "Timeout", "N/A")
				case Error(error)             => StatusEntry(time, "Error", error)
			}
		}.toSeq
		analyticTable.items = ObservableBuffer(entries)
		analyticTime.cellValueFactory = { v => ObjectProperty(v.value.time) }
		analyticEvent.cellValueFactory = { v => StringProperty(v.value.event) }
		analyticReason.cellValueFactory = { v => StringProperty(v.value.reason) }
	}

	context.service.foreach { gsOp =>
		(gsOp, node) match {
			case (None, _)                                         => showModal("No connection")
			case (_, NodeError(id, reason))                        => showModal(s"Node $id: $reason")
			case (Some(gs), NodeHistory(id, target, remark, _, _)) =>
				println(s"$id")
				modal.visible = false
				nodeInfo.text =
					s"""Node $id
					   |${target.host}:${target.port}""".stripMargin
				infoRemark.text = remark


				type R = guard.RemoteResult[Entity.TelemetrySeries]

				def pull(start: Option[ZonedDateTime]): Task[(ZonedDateTime, guard.RemoteResult[Entity.TelemetrySeries])] = {
					for {
						now <- Task.eval {ZonedDateTime.now()}
						result <- gs.nodes().telemetry(id, Bound(start)).map { v => println(start + "->" + (v.asInstanceOf[Found[TelemetrySeries]].a.series.keys)); v }
					} yield now -> result
				}

				val source: Observable[Unit] = Observable.now(()) ++ gs.events.filter {
					case NodeUpdated(ns) => ns.contains(node.id)
					case _               => false
				}.map { _ => () }
				source.scanTask(pull(None)) { case ((last, _), _) => pull(Some(last)) }
					.map {_._2}
					.observeOn(Schedulers.JavaFx)
					.doOnError {_.printStackTrace()}
					.foreach { v =>
						v match {
							case ConnectionError(e)  =>
							case ServerError(reason) =>
							case ClientError(reason) =>
							case DecodeError(reason) =>
							case NotFound            =>
							case Found(result)       => println("!!" + result.series)
								result.series.lastOption.map {_._2}.foreach(updateStatus)
								updateHistorySeries(result.series)
								updateLogs(result.series)
								updateAnalytic(result.series)

						}


					}


		}


	}

}
