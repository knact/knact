package io.knact.guard.cli

import com.google.common.base.Throwables
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.Window.Hint
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.input.KeyStroke
import io.knact.guard.Entity.TimeSeries
import io.knact.guard.GuardService.{NodeError, NodeHistory, NodeItem, StatusEntry}
import io.knact.guard.Telemetry._
import io.knact.guard.{ClientError, ConnectionError, DecodeError, Found, GuardService, NotFound, Percentage, ServerError, Telemetry}
import monix.execution.Scheduler.Implicits.global

import scala.collection.JavaConverters._


class NodeDetailWindow(val node: NodeItem, val gui: MultiWindowTextGUI, val service: GuardService) {

	private val window = new BasicWindow(s"Node ${node.id} - press [ESC] to close")
	window.setCloseWindowWithEscape(true)

	window.setHints(Seq( Hint.CENTERED).asJava)

	private val panel = new Panel(new BorderLayout)
	window.setComponent(panel)

	private val info    = new Panel()
	private val stats   = new Panel()
	private val history = new Panel()

	panel.addComponent(info.withBorder(Borders.singleLine("Info")), BorderLayout.Location.TOP)
	panel.addComponent(stats.withBorder(Borders.singleLine("Stats")), BorderLayout.Location.CENTER)
	panel.addComponent(history.withBorder(Borders.singleLine("History")), BorderLayout.Location.BOTTOM)

	private def updateInfo(status: Status): Unit = {
		info.removeAllComponents()
		val fl = node match {
			case NodeError(id, reason)                 => s"Node $id failed: $reason"
			case NodeHistory(id, target, remark, _, _) =>
				s"Node $id ${target.username}@${target.host}${if (remark.isEmpty) "" else "\n" + remark}"
		}
		val sl = status match {
			case Offline                 => "(offline)"
			case Timeout                 => "(timeout)"
			case Error(_)                => "(error, see stats)"
			case Online(_, _, telemetry) => telemetry.arch
		}
		info.addComponent(new Label(
			s"""$fl
			   |$sl""".stripMargin))
	}

	private def updateStatus(status: Status): Unit = {
		stats.removeAllComponents()
		val c: Component = status match {
			case Offline                          => new Label("Node offline")
			case Timeout                          => new Label("Node timeout")
			case Error(error)                     => new Label(s"Guard returned with error: $error")
			case Online(state, reason, telemetry) =>
				val cols = new Panel(new LinearLayout(Direction.HORIZONTAL))

				val col1 = new Panel(new LinearLayout(Direction.VERTICAL)).addTo(cols)
				new Label(s"CPU :${Lanterna.fmtPercent(telemetry.cpuPercent)} ").addTo(col1)
				new Label(s"RAM :${Lanterna.fmtPercent(telemetry.memPercent)} ").addTo(col1)
				new Label(s"Disk:${Lanterna.fmtPercent(telemetry.diskPercent)} ").addTo(col1)
				val col2 = new Panel(new LinearLayout(Direction.VERTICAL)).addTo(cols)
				new Label(s"Uptime    :${telemetry.uptime} ").addTo(col2)
				new Label(s"Users     :${telemetry.users} ").addTo(col2)
				new Label(s"Processors:${telemetry.processorCount} ").addTo(col2)

				new Label(s"$state : ${reason.getOrElse(" - ")}")
					.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.End))
					.addTo(cols)
				cols

		}
		stats.addComponent(c)
	}

	private def updateHistory(series: TimeSeries[Telemetry.Status])  = {
		history.removeAllComponents()
		val c = new Panel(new LinearLayout(Direction.HORIZONTAL))
		val events = new ActionListBox()
		events.withBorder(Borders.singleLine("Events")).addTo(c)

		series.map { case (time, s) =>
			s match {
				case Online(state, reason, _) => s"$time :${state.toString} : ${reason.getOrElse(" - ")}"
				case Offline                  => s"$time :Offline"
				case Timeout                  => s"$time :Timeout"
				case Error(error)             => s"$time :Error=$error"
			}
		}.toSeq.reverse.foreach { l => events.addItem(l, () => {}) } // newest on top


		def mkHistogram(title: String, f: Telemetry => Percentage) = {
			val ls = new ActionListBox()
			ls.setPreferredSize(new TerminalSize(15, 30))
			series.map { case (time, s) => s match {
				case Online(_, _, te) => time -> f(te)
				case _                => time -> 0.0
			}
			}.map { case (_, p) => Lanterna.fmtPercent(p) + "█" * ((p / 100.0) * 15.0).toInt }
				.foreach(s => ls.addItem(s, () => {}))
			ls.withBorder(Borders.singleLine(title))
		}

		mkHistogram("CPU", _.cpuPercent).addTo(c)
		mkHistogram("RAM", _.memPercent).addTo(c)
		mkHistogram("Disk", _.diskPercent).addTo(c)

		history.setPreferredSize(new TerminalSize(100, 30))
		history.addComponent(c)
		events
	}

	service.nodes().observeSingle(node.id).foreach {
		case ConnectionError(e)  => window.setComponent(new Label(Throwables.getStackTraceAsString(e)))
		case ServerError(reason) => window.setComponent(new Label(s"Server error:$reason"))
		case ClientError(reason) => window.setComponent(new Label(s"Client error:$reason"))
		case DecodeError(reason) => window.setComponent(new Label(s"Decode error:$reason"))
		case NotFound            => window.setComponent(new Label(s"Node ${node.id} no longer exists"))
		case Found(result)       => gui.getGUIThread.invokeLater { () =>
			result.series.lastOption.map {_._2}.foreach { v =>
				updateInfo(v)
				updateStatus(v)
			}
			updateHistory(result.series).takeFocus()

		}
	}



	gui.addWindowAndWait(window)

}
