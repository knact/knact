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


	def mkScene() = new Scene(FXMLView(
		Resources.getResource("Frame.fxml"),
		new DependenciesByType(Map(typeOf[StageContext] -> new StageContext {
			override val stage: Stage = Main.stage
		}))))

	stage = new JFXApp.PrimaryStage
	stage.title = "Knact guard client"
	stage.scene = mkScene()
	logger.info("Scene ready")


}
