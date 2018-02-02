package io.knact.linux

import java.time.ZonedDateTime

import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.io.Source

class dateSpec extends FlatSpec with Matchers with EitherValues {


	"date" should "parse" in {
		val iso8601 = "2018-01-27T13:06:47+00:00"
		val mock = MockShell.mkMockedShell(Source.fromString(iso8601))
		date.command.run(mock).right.value should be(ZonedDateTime.parse(iso8601))


	}

}
