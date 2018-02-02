package io.knact.linux

import java.time.ZonedDateTime

import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.io.Source

class envSpec extends FlatSpec with Matchers with EitherValues {

	behavior of "env"

	it should "parse simple" in {
		val kvm =
			"""A=B
			  |B=C
			  |C=D=E
			  |A1=2
			  |_=A=B=C
			""".stripMargin
		val mock = MockShell.mkMockedShell(Source.fromString(kvm))
		env.command.run(mock).right.value shouldBe Map(
			"A" -> "B",
			"B" -> "C",
			"C" -> "D=E",
			"A1" -> "2",
			"_" -> "A=B=C"
		)
	}

	it should "parse sample corpus" in {
		val mock = MockShell.mkMockedShell(Source.fromResource("env1.txt"))
		val value = env.command.run(mock)
		value.right.value.size shouldBe 53
	}

}
