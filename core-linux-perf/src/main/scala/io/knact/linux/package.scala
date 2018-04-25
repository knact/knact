package io.knact


import io.knact.Basic.{ConsoleIO, ConsoleNode}
import net.sf.expectit
import net.sf.expectit.ExpectBuilder
import net.sf.expectit.filter.Filters.{removeColors, removeNonPrintable}
import net.sf.expectit.matcher.{Matcher, SimpleResult}

package object linux {

	private[knact] def mkExpect(io: ConsoleIO) = new ExpectBuilder()
		.withOutput(io.out)
		.withInputs(io.in, io.err)
		.withInputFilters(removeColors(), removeNonPrintable())
		.withExceptionOnFailure()
		.build()


	//	private[knact] final val EOT_CHAR = "\u2301"
	//	private[knact] final val EOT_STR  = s"\n$EOT_CHAR\n"


	private final def eofWithFirstLineWorkaround() = new Matcher[expectit.Result] {
		var firstLine = true
		override def matches(input: String, isEof: Boolean): expectit.Result = {
			//XXX isEof can sometimes be true at the first read so we can't use Matchers.eof()
			if (firstLine && input.isEmpty && isEof) return SimpleResult.failure(input, false)
			firstLine = false
			if (isEof) SimpleResult.success(input, input, input)
			else SimpleResult.failure(input, false)
		}
	}

	/**
	  * Sends a command and reads fully until EOF
	  * @param s the command to execute
	  * @return stdout mapped to a string
	  */
	def sendAndReadUntilEOF(s: String)(implicit n: ConsoleNode): String = {
		val io = n.exec(s)
		val expect = mkExpect(io)
		val input = expect.expect(eofWithFirstLineWorkaround()).getInput
		io.close()
		expect.close()
		input
	}
	//	private[knact] def sendAndReadUntilEnd(s: String)(implicit n: ConsoleNode): String =
	//		mkExpect(n.io).sendLine(s"$s;echo '$EOT_CHAR'")
	//			.expect((input: String, _: Boolean) => {
	//				println("[[" + input + "]]")
	//				if (input.endsWith(EOT_STR)) {
	//					val str = input.substring(0, input.length - EOT_STR.length)
	//					SimpleResult.success(str, str, str)
	//				} else SimpleResult.failure(input, false)
	//			}).getInput

}
