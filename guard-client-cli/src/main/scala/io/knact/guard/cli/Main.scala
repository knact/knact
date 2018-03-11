package io.knact.guard.cli

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.Window.Hint
import com.googlecode.lanterna.gui2.dialogs.{MessageDialogBuilder, MessageDialogButton}
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, Terminal}
import com.googlecode.lanterna.gui2.Borders.doubleLine
import com.googlecode.lanterna.gui2.Borders.singleLine
import com.googlecode.lanterna.gui2.Borders.singleLineBevel
import com.googlecode.lanterna.gui2.GridLayout.createHorizontallyFilledLayoutData

import scala.collection.JavaConverters._
import com.googlecode.lanterna.TextColor
import com.typesafe.scalalogging.LazyLogging
import io.knact.guard.cli.Lanterna.width

import scala.util.{Failure, Try}

object Main extends App with LazyLogging {

	case class Session()

	case class Credential(url: String)


//	private def obtainRemote(credential : Credential):Try[Session]  =  {
//		return authenticate(credential.complete() ? credential : askCredential(credential))
//		.recoverWith(e -> {
//			new MessageDialogBuilder()
//				.setTitle("Login failed")
//				.setText(Throwables.getStackTraceAsString(e))
//				.addButton(MessageDialogButton.Retry)
//				.build()
//				.showDialog(gui);
//			return obtainRemote(askCredential(credential));
//		});
//	}

	private def askCredential(filled: Credential) = {
		val panel = new Panel(new GridLayout(3))
		val window = Lanterna.newDialog("Login", panel)
		new Label("URL").addTo(panel)
		val url = new TextBox(width(40), filled.url).setLayoutData(createHorizontallyFilledLayoutData(2)).addTo(panel)
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
		Credential(url.getText)
	}


	val terminal = new DefaultTerminalFactory()
		.setForceTextTerminal(false)
		.createTerminal()

	val screen = new TerminalScreen(terminal)
	screen.startScreen()


	val gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.GREEN))

	val windowSSH = new BasicWindow("Connection Screen")
	windowSSH.setHints(Seq(Hint.EXPANDED).asJava)

	val panelSSH = new Panel()
	panelSSH.setLayoutManager(new GridLayout(2))

	panelSSH.addComponent(new Label("SSH:").setLabelWidth(10))
	val sshBox = new TextBox("Enter SSH Credentials Here!").addTo(panelSSH)


}
