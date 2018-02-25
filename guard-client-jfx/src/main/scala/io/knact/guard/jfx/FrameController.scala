package io.knact.guard.jfx

import com.google.common.io.Resources

import scalafx.Includes._
import scalafx.scene.control._
import scalafx.scene.layout.VBox
import scalafx.stage.Stage
import scalafxml.core.macros.sfxml
import scalafxml.core.{DependenciesByType, FXMLView}

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
					  tabs: TabPane,
					  stage: Stage,
					 ) {


	connectTo.onAction = handle {
		new TextInputDialog {
			title = "Server URL"
			headerText = "Connect to a Knact guard server"
			contentText = "URL"
		}.showAndWait().foreach { url =>
			println(s"url $url")
			var root = FXMLView(
				Resources.getResource("Overview.fxml"),
				new DependenciesByType(Map()))
			tabs.tabs += new Tab {
				text = "Foo"
				content = root
			}.delegate
		}
	}

}
