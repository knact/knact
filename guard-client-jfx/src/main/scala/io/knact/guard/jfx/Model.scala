package io.knact.guard.jfx

import java.time.ZonedDateTime

import cats.kernel.Semigroup
import io.knact.guard.Entity.{Id, Node, Target, TimeSeries}
import io.knact.guard.Telemetry.Status
import io.knact.guard.{ByteSize, GuardService, Path}

import scala.collection.immutable
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.stage.Stage

object Model {

	//XXX these are needed because @sfxml does the wrong thing when injecting scalafx stuff
	trait StageContext {val stage: Stage}
	trait AppContext extends StageContext {
		val service: ReadOnlyObjectProperty[Option[GuardService]]
		//		val procedures : ReadOnlyObjectProperty[List[Procedure]]
	}


	sealed trait NodeListLayout extends enumeratum.EnumEntry
	object NodeListLayout extends enumeratum.Enum[NodeListLayout] {
		override def values = findValues
		case object Stat extends NodeListLayout
		case object Graph extends NodeListLayout
		case object Combined extends NodeListLayout
	}

}
