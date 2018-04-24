package io.knact.guard.cli

import com.googlecode.lanterna.gui2.Window.Hint
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.table.{Table, TableModel}
import io.knact.guard.Entity.{NodeUpdated, PoolChanged}
import io.knact.guard.GuardService
import io.knact.guard.GuardService.{NodeError, NodeHistory}
import io.knact.guard.Telemetry.Online
import monix.execution.Scheduler.Implicits.global
import shapeless.Sized
import shapeless._
import shapeless.syntax.sized._

import scala.collection.JavaConverters._

class NodeWindow(val gui: MultiWindowTextGUI, service: GuardService) {

	val windowSSH = new BasicWindow("Connection Screen")
	windowSSH.setHints(Seq(Hint.EXPANDED).asJava)

	val panel = new Panel(new BorderLayout)
	windowSSH.setComponent(panel)


	val good = new Table[String]("")
	good.addTo(panel)
	good.setLayoutData(BorderLayout.Location.CENTER)

	good.setSelectAction(() => {
		println("Hey")
	})

	val bad = new Table[String]("")
	bad.addTo(panel)
	bad.setLayoutData(BorderLayout.Location.BOTTOM)

	good.takeFocus()

	private def bindModel(cols: Seq[String], rows: Seq[Seq[String]]): TableModel[String] = {
		val value = new TableModel[String]("id", "node", "cpu", "mem", "disk")
		rows.foreach { row => value.addRow(row: _*) }
		value
	}

	final val NodeColumns  = Seq("id", "node", "cpu", "mem", "disk")
	final val ErrorColumns = Seq("id", "reason")

	service.nodes().observe().foreach {
		case Left(e) =>

		case Right(nodes) =>

			gui.getGUIThread.invokeLater { () =>


				val sorted = nodes.toList.sortBy {_._1}.map {_._2}
				bad.setTableModel(bindModel(ErrorColumns, sorted.collect {
					case NodeError(id, reason) => Seq(id.toString, reason)
				}))
				good.setTableModel(bindModel(NodeColumns, sorted.collect {
					case NodeHistory(id, target, _, status, _) =>
						val identity = s"${target.username}@${target.host}"
						status.lastOption.map {_._2}.collect { case v: Online =>
							Seq(
								id.toString, identity,
								v.telemetry.cpuPercent.toString + "%",
								v.telemetry.memPercent.toString + "%",
								v.telemetry.diskPercent.toString + "%")
						}.getOrElse(Seq(id.toString, identity, "-", "-", "-"))
				}))

			}


	}

	gui.addWindowAndWait(windowSSH)

	//	service.events.foreach {
	//		case PoolChanged(pool)  =>
	//		case NodeUpdated(delta) =>
	//	}


}
