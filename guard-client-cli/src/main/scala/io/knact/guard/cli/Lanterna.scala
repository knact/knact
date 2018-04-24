package io.knact.guard.cli

import java.util.Collections._

import com.google.common.base.Throwables
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.{BasicWindow, Component, WindowBasedTextGUI}
import com.googlecode.lanterna.gui2.Window.Hint
import com.googlecode.lanterna.gui2.dialogs.{MessageDialogBuilder, MessageDialogButton}
import com.googlecode.lanterna.gui2.table.{Table, TableModel}
import io.knact.guard.Percentage

object Lanterna {

	def width(value: Int) = new TerminalSize(value, 1)

	def newDialog(title: String, component: Component): BasicWindow = {
		val window = newDialog(title)
		window.setComponent(component)
		window
	}

	def newDialog(title: String): BasicWindow = {
		val window = new BasicWindow()
		window.setCloseWindowWithEscape(true)
		window.setHints(singletonList(Hint.CENTERED))
		window
	}

	def mkTable(): Table[String] = {
		val table = new Table[String]("")
		table.setCellSelection(true)
		table.setRenderer(new ResizingTableRenderer[String])
		table
	}

	def bindModel(cols: Seq[String], rows: Seq[Seq[String]]): TableModel[String] = {
		val value = new TableModel[String](cols: _*)
		rows.foreach { row => value.addRow(row: _*) }
		value
	}


	def mkErrorDialog( title: String, e: Throwable): MessageDialogBuilder = {
		new MessageDialogBuilder()
			.setTitle(title)
			.setText(Throwables.getStackTraceAsString(e))
	}


	def fmtPercent(x: Percentage): String = f"$x%.1f%%"

}
