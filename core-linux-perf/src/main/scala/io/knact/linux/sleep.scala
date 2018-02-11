package io.knact.linux


import io.knact.Basic.ConsoleNode
import io.knact.Command
import net.sf.expectit.matcher.Matchers


object sleep {

	final val command: Int => Command[ConsoleNode, Unit] = {
		ms =>
			Command[ConsoleNode, Unit] { implicit n =>
				mkExpect(n.exec(s"sleep $ms")).expect(Matchers.eof())
				Right(())
			}
	}

}
