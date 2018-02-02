package io.knact.linux

import io.knact.Basic.{MockConsoleTransport, RuntimeShell}
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.io.Source

class topSpec extends FlatSpec with Matchers with EitherValues {

	behavior of "top"

	val topSamples = Table(
		"file",
		"top1.txt",
		"top2.txt",
		"top3.txt",
		"top4.txt",
		"top5.txt",
	)

	it should "parse sample corpus" in {
		forAll(topSamples) { file =>
			val mock = MockShell.mkMockedShell(Source.fromResource(file))
			val value = top.command.run(mock)
			println(file+value)
			value.right.value
		}
	}


}
