package io.knact.guard

import java.time.Duration

import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.parser.decode
import io.circe.syntax._
import io.knact.guard.Entity.{Procedure, id}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{EitherValues, FlatSpec, Matchers}

class ModelSpec extends FlatSpec with Matchers with EitherValues {

	private final val ExhibitJsonBijectiveProperties = "serialise to JSON and back"


	private final val Utf8Text = "吾輩は猫である\uD83D\uDE42"

	private def shouldBeBijective[T](input: T, expected: String)(implicit enc: Encoder[T], dec: Decoder[T]) = {
		val serialised = input.asJson.noSpaces
		serialised shouldBe expected
		decode[T](serialised).right.value shouldBe input
	}

	"Procedure" should ExhibitJsonBijectiveProperties in {
		forAll(Table(
			("input", "expected"),
			(Procedure(id(1), "", "", "", Duration.ZERO),
				"""{"id":1,"name":"","remark":"","code":"","timeout":"PT0S"}"""),
			(Procedure(id(0), "foo", "bar", "baz", Duration.ofDays(365)),
				"""{"id":0,"name":"foo","remark":"bar","code":"baz","timeout":"PT8760H"}"""),
			(Procedure(id(-1), Utf8Text, Utf8Text, Utf8Text, Duration.ofNanos(10)),
				s"""{"id":-1,"name":"$Utf8Text","remark":"$Utf8Text","code":"$Utf8Text","timeout":"PT0.00000001S"}""")
		))(shouldBeBijective[Procedure])
	}

	//	"Node" should ExhibitJsonBijectiveProperties in {
	//		forAll(Table(
	//			("input", "expected"),
	//			// TODO write me
	//		))(shouldBeBijective[Node])
	//	}
	//
	//	"Group" should ExhibitJsonBijectiveProperties in {
	//		forAll(Table(
	//			("input", "expected"),
	//			// TODO write me
	//		))(shouldBeBijective[Group])
	//	}

}
