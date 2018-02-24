package io.knact.guard.jfx

import java.time.ZonedDateTime

import scalafx.scene.chart.StackedAreaChart
import scalafx.scene.control.{Label, TreeTableColumn, TreeTableView}
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class NodeController(root: VBox,
					 overviewChart: StackedAreaChart[ZonedDateTime, Double],
					 cpu: Label,
					 ram: Label,
					 disk: Label,
					 uptime: Label,
					 load: Label,
					 terminalPane: StackPane,
					 logs: TreeTableView[String],
					 logPath: TreeTableColumn[String, String],
					 logSIze: TreeTableColumn[String, String],
					 logPane: StackPane,
					) {


}
