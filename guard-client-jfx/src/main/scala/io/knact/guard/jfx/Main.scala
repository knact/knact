package io.knact.guard.jfx

import com.google.common.io.Resources
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.jfx.Model.StageContext

import scala.reflect.runtime.universe.typeOf
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.stage.Stage
import scalafxml.core.{DependenciesByType, FXMLView}

object Main extends JFXApp with LazyLogging {



	stage = new JFXApp.PrimaryStage
	private val ctx = new StageContext {
		override val stage: Stage = Main.stage
	}

	stage.title = "Knact JFX"
	stage.scene = new Scene(FXMLView(
		Resources.getResource("Frame.fxml"),
		new DependenciesByType(Map(typeOf[StageContext] -> ctx)))
	)
	//	stage.icons += new Image(Resources.getResource("icon.png").toExternalForm)
	logger.info("Scene ready")


}
