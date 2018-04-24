package io.knact.guard.cli

import com.google.common.base.Throwables
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.GridLayout.createHorizontallyFilledLayoutData
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.dialogs.{MessageDialogBuilder, MessageDialogButton}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.GuardService
import io.knact.guard.cli.Lanterna.width
import monix.execution.Scheduler.Implicits.global
import scala.util.{Failure, Try}

object Main extends App with LazyLogging {
	case class Credential(url: String)


	private def askCredential(gui: MultiWindowTextGUI, default: Credential): GuardService = {
		val panel = new Panel(new GridLayout(3))
		val window = Lanterna.newDialog("Login", panel)
		new Label("URL").addTo(panel)
		val url = new TextBox(width(40), default.url).setLayoutData(createHorizontallyFilledLayoutData(2)).addTo(panel)
		new Label("Username").addTo(panel)
		val username = new TextBox(width(25), "").setLayoutData(createHorizontallyFilledLayoutData(2)).addTo(panel)
		new Label("Password").addTo(panel)
		val password = new TextBox(width(25), "").setLayoutData(createHorizontallyFilledLayoutData(2)).setMask('*').addTo(panel)
		// XXX one day
		username.setEnabled(false)
		password.setEnabled(false)
		url.takeFocus
		new EmptySpace().addTo(panel);
		new Button("Login", () => window.close()).addTo(panel)
		new Button("Cancel", () => {
			Try {gui.getScreen.stopScreen()}
				.recoverWith { case e =>
					// not really a recovery, oh well
					logger.error("Cancel did not succeed", e)
					Failure(e)
				}
			sys.exit()
		}).addTo(panel)
		gui.addWindowAndWait(window)
		val current = Credential(url.getText)
		GuardService(current.url) match {
			case Left(value)  =>
				Lanterna.mkErrorDialog("Login failed", value)
					.addButton(MessageDialogButton.Retry)
					.build().showDialog(gui)
				askCredential(gui, current);
			case Right(value) => value
		}
	}


	override def main(args: Array[String]): Unit = {

		val native = args.toList match {
			case Nil              => false
			case "-native" :: Nil => true
			case _                =>
				println(s"Unsupported argument:${args.mkString(" ")}")
				sys.exit()
		}

		val terminal = new DefaultTerminalFactory()
			.setForceTextTerminal(native)
			.createTerminal()

		val screen = new TerminalScreen(terminal)
		screen.startScreen()


		val gui = new MultiWindowTextGUI(screen,
			new DefaultWindowManager(),
			new EmptySpace(TextColor.Indexed.fromRGB(52, 102, 163)))


		new NodeMasterWindow(gui, askCredential(gui, Credential("http://localhost:8080/api")))
	}


}
