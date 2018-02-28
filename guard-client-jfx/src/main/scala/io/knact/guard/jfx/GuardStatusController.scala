package io.knact.guard.jfx

import java.time.ZonedDateTime

import io.knact.guard.Entity.Node

import scalafx.scene.chart.StackedAreaChart
import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class GuardStatusController(root: VBox,
							history: StackedAreaChart[ZonedDateTime, Double]) {


}
