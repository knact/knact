package io.knact.guard.jfx

import io.knact.guard.Entity.Node

import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class FrameController(root: VBox,
					  connectTo: MenuItem,
					  recentServers: Menu,
					  export: MenuItem,
					  preferences: MenuItem,
					  quit: MenuItem,
					  groupInNewWindow: CheckMenuItem,
					  manual: MenuItem,
					  about: MenuItem,
					  sort: SplitMenuButton,
					  filter: TextField,
					  nodes: ListView[Node],
					  nodeStatus: Label,
					  nodePane: StackPane) {


}
