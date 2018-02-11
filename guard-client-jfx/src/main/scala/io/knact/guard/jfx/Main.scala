package io.knact.guard.jfx

import com.google.common.io.Resources
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.runtime.universe.typeOf
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{DependenciesByType, FXMLView}
import scalafx.Includes._

object Main extends JFXApp with LazyLogging {

	var root = FXMLView(
		Resources.getResource("Frame.fxml"),
		new DependenciesByType(Map(
			typeOf[PrimaryStage] -> stage)))


	stage = new JFXApp.PrimaryStage()
	stage.title = "Knact JFX"
	stage.scene = new Scene(root)
	//	stage.icons += new Image(Resources.getResource("icon.png").toExternalForm)
	logger.info("Scene ready")


}
