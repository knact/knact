package io.knact.guard.jfx


import com.google.common.io.Resources
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.Service
import org.controlsfx.control.SegmentedButton
import org.http4s.Uri

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.stage.Stage
import scalafxml.core.macros.sfxml
import scalafxml.core.{DependenciesByType, FXMLView}
import monix.execution.Scheduler.Implicits.global

@sfxml(additionalControls = List("org.controlsfx"))
class FrameController(root: VBox,
					  newWindow: MenuItem,
					  export: MenuItem,
					  preferences: MenuItem,
					  quit: MenuItem,

					  groupInNewWindow: CheckMenuItem,
					  manual: MenuItem,
					  about: MenuItem,

					  views: SegmentedButton,
					  nodeStatus: ToggleButton,
					  guardStatus: ToggleButton,
					  log: ToggleButton,

					  serverUrl: TextField,
					  viewPane: StackPane,
					  stage: Stage,
					 ) extends LazyLogging {


	private val nodeStatusView: Node = FXMLView(
		Resources.getResource("NodeStatus.fxml"),
		new DependenciesByType(Map()))

	private val guardStatusView: Node = FXMLView(
		Resources.getResource("GuardStatus.fxml"),
		new DependenciesByType(Map()))

	private val logView: Node = FXMLView(
		Resources.getResource("Log.fxml"),
		new DependenciesByType(Map()))

	views.getToggleGroup.selectedToggle.onChange { (_, previous, current) =>
		current match {
			case `nodeStatus`  => viewPane.children = Seq(nodeStatusView)
			case `guardStatus` => viewPane.children = Seq(guardStatusView)
			case `log`         => viewPane.children = Seq(logView)
			case null          => views.getToggleGroup.selectToggle(previous)
			case unexpected    => sys.error(s"Unexpected toggle $unexpected")
		}
	}


	serverUrl.onAction = handle {

		import javafx.scene.Parent

		log.selected = true
		Uri.fromString(serverUrl.getText) match {
			case Left(fail)   =>
				logger.info(s"${
					fail.message
				}")
			case Right(value) =>
				logger.info(s"ok $value")
				new Service(value).status().toIO.unsafeRunSync()

		}

	}


	//	new SegmentedButton(grid, masterDetail, log)

	//	connectTo.onAction = handle {

	//	}

}
