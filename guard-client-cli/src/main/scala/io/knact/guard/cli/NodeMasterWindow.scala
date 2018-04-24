package io.knact.guard.cli

import com.google.common.base.Throwables
import com.googlecode.lanterna.gui2.Window.Hint
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import com.googlecode.lanterna.gui2.table.{Table, TableModel}
import io.knact.guard.Entity.{Node, NodeUpdated, PoolChanged}
import io.knact.guard.{Entity, GuardService, Percentage}
import io.knact.guard.GuardService.{NodeError, NodeHistory}
import io.knact.guard.Telemetry.Online
import monix.execution.Scheduler.Implicits.global
import shapeless.Sized
import shapeless._
import shapeless.syntax.sized._

import scala.collection.JavaConverters._
import scala.util.Try

object NodeMasterWindow {


}

class NodeMasterWindow(val gui: MultiWindowTextGUI, service: GuardService) {

	private final val NodeColumns  = Seq("id", "node", "cpu", "mem", "disk")
	private final val ErrorColumns = Seq("id", "reason")

	private val window = new BasicWindow("Knact guard server - press [ESC] to exit")
	window.setHints(Seq(Hint.EXPANDED).asJava)

	private val panel = new Panel(new BorderLayout)
	window.setComponent(panel)


	private val message = new Label(s"Connected to " + service.baseUri)
		.setLayoutData(BorderLayout.Location.TOP).addTo(panel)

	private val live = Lanterna.mkTable()
	live.withBorder(Borders.singleLine("Live"))
		.setLayoutData(BorderLayout.Location.CENTER).addTo(panel)


	private val failed = Lanterna.mkTable()
	failed.withBorder(Borders.singleLine("Failed"))
		.setLayoutData(BorderLayout.Location.BOTTOM).addTo(panel)

	live.takeFocus()

	service.nodes().observe().foreach {
		case Left(e)      => message.setText(s"${service.baseUri} : " + e)
		case Right(nodes) =>

			gui.getGUIThread.invokeLater { () =>


				val sorted = nodes.toList.sortBy {_._1}.map {_._2}
				failed.setTableModel(Lanterna.bindModel(ErrorColumns, sorted.collect {
					case NodeError(id, reason) => Seq(id.toString, reason)
				}))
				live.setTableModel(Lanterna.bindModel(NodeColumns, sorted.collect {
					case NodeHistory(id, target, _, status, _) =>
						val identity = s"${target.username}@${target.host}"
						status.lastOption.map {_._2}.collect { case v: Online =>
							Seq(
								id.toString, identity,
								Lanterna.fmtPercent(v.telemetry.cpuPercent),
								Lanterna.fmtPercent(v.telemetry.memPercent),
								Lanterna.fmtPercent(v.telemetry.diskPercent))
						}.getOrElse(Seq(id.toString, identity, "-", "-", "-"))
				}))

				live.setSelectAction(() => {
					val selectedRow = live.getTableModel.getRow(live.getSelectedRow)
					(for {
						head <- selectedRow.asScala.headOption
						id <- Try {Entity.id[Node, Long](head.toLong)}.toOption
						node <- nodes.get(id)
					} yield node) match {
						case Some(node) => new NodeDetailWindow(node, gui, service)
						case None       =>
							Lanterna.mkErrorDialog("Unable to obtain node id",
								new Exception(s"Column 0 should be id tagged with Node but was not, columns are $selectedRow"))
								.addButton(MessageDialogButton.Continue)
								.build().showDialog(gui)
					}
				})
			}
	}
	window.setCloseWindowWithEscape(true)
	gui.addWindowAndWait(window)
	service.terminate()
	println("Last window released, exiting CLI mode")
	sys.exit()
}

