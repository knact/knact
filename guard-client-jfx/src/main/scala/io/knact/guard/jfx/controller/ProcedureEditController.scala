package io.knact.guard.jfx.controller

import javafx.scene.control.Label

import scalafx.scene.control.{Button, ChoiceBox, TextArea, TextField}
import scalafx.scene.layout.{StackPane, VBox}
import scalafxml.core.macros.sfxml

@sfxml
class ProcedureEditController(private val root: VBox,
							  private val title: Label,
							  private val titleField: TextField,
							  private val remarkField: TextArea,
							  private val codePane: StackPane,
							  private val reset: Button,
							  private val save: Button,
							 ) {


}
