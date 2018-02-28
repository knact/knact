package io.knact.guard.jfx

import io.knact.guard.Entity.Node
import io.knact.guard.Service

import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class OverviewController(root: SplitPane,
						 filter: TextField,
						 nodes: ListView[Node],
						 nodeStatus: Label,
						 nodePane: StackPane) {




}
