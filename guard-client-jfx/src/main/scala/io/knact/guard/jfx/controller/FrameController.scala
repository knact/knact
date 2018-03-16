package io.knact.guard.jfx.controller

import com.google.common.io.Resources._
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard._
import io.knact.guard.jfx.Model.{AppContext, StageContext}
import io.knact.guard.{Entity, GuardService}
import io.knact.guard.jfx.RichScalaFX._
import monix.execution.Scheduler.Implicits.global
import org.controlsfx.control.SegmentedButton

import scala.reflect.runtime.universe.typeOf
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.concurrent.Task
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.stage.Stage
import scalafx.{concurrent => sfxc}
import scalafxml.core.macros.sfxml
import scalafxml.core.{DependenciesByType, FXMLView}

@sfxml(additionalControls = List("org.controlsfx"))
class FrameController(private val root: VBox,
					  private val newWindow: MenuItem,
					  private val export: MenuItem,
					  private val preferences: MenuItem,
					  private val quit: MenuItem,

					  private val groupInNewWindow: CheckMenuItem,
					  private val manual: MenuItem,
					  private val about: MenuItem,

					  private val views: SegmentedButton,
					  private val nodeOverview: ToggleButton,
					  private val guardOverview: ToggleButton,
					  private val logOverview: ToggleButton,

					  private val serverUrl: TextField,
					  private val viewPane: StackPane,
					  private val stageContext: StageContext,
					 ) extends LazyLogging {


	private val serviceProperty: ObjectProperty[Option[GuardService]] = ObjectProperty(None)

	private val context = new AppContext {
		override val stage  : Stage                                        = stageContext.stage
		override val service: ReadOnlyObjectProperty[Option[GuardService]] = serviceProperty
	}

	private val dependencies = new DependenciesByType(Map(
		typeOf[AppContext] -> context
	))


	private val nodeMasterView : Node = FXMLView(getResource("NodeMaster.fxml"), dependencies)
	private val guardStatusView: Node = FXMLView(getResource("GuardOverview.fxml"), dependencies)
	private val logView        : Node = FXMLView(getResource("LogOverview.fxml"), dependencies)

	views.getToggleGroup.selectedToggle.onChange { (_, previous, current) =>
		current match {
			case `nodeOverview`  => viewPane.children = Seq(nodeMasterView)
			case `guardOverview` => viewPane.children = Seq(guardStatusView)
			case `logOverview`   => viewPane.children = Seq(logView)
			case null            => views.getToggleGroup.selectToggle(previous)
			case unexpected      => sys.error(s"Unexpected toggle $unexpected")
		}
	}

	serverUrl.text = "http://localhost:8080/api"
	serverUrl.onAction = handle {

		val url = serverUrl.getText
		val task: Task[Either[Throwable, GuardService]] = sfxc.Task {GuardService(url)}

		context.stage.title <== serviceProperty.map { o => o.map { v => v.baseUri.toString() }.getOrElse("") }

		serverUrl.disable <== task.running
		task.value.onChange { (_, _, v) =>
			// stop the old connection first
			serviceProperty.value.foreach {_.terminate()}
			v match {
				case Left(e)        =>
					logger.error(s"Unable to connect to $url", e)
					logOverview.selected = true
					serviceProperty.value = None
					Platform.runLater {serverUrl.requestFocus()}
				case Right(service) =>
					logger.info(s"Connected to $service")
					serviceProperty.value = Some(service)
			}
		}
		global.execute(task)
	}
}
