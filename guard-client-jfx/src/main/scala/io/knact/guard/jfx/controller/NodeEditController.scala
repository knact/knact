package io.knact.guard.jfx.controller

import javafx.scene.control.Label

import scalafx.scene.control.{Button, ChoiceBox, TextArea}
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class NodeEditController(private val root: VBox,
						 private val title: Label,
						 private val targetType: ChoiceBox[String],
						 private val targetConfigPane: StackPane,
						 private val remarkField: TextArea,
						 private val reset: Button,
						 private val save: Button,
						) {


}
