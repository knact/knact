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

class ServiceSpec extends FlatSpec with Matchers with EitherValues {


}
