package io.knact.guard.cli

import java.util.Collections._

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.{BasicWindow, Component}
import com.googlecode.lanterna.gui2.Window.Hint

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


}
