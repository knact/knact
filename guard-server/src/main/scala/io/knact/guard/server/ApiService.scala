package io.knact.guard.server

import cats.implicits._
import org.http4s.dsl.Http4sDsl
import monix.eval.Task
import org.http4s.HttpService
import org.http4s._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.knact.guard._

import io.circe.java8.time._


class ApiService(val groupRepo: GuardGroupRepo) extends Http4sDsl[Task] {


	// GET     group          :: Seq[GroupView]
	// GET     group/{id}     :: Group
	// POST    group	      :: Seq[Group] -> Seq[EntityId]  // update a group if found; add if no id specified,
	// DELETE  group          :: Seq[EntityId] // delete all groups
	// DELETE  group/{id}     :: EntityId  // delete specific group


	// GET     group/{id}/node/         ::Seq[Node]
	// POST    group/{id}/node/         ::Seq[Node] -> Seq[EntityId] // update a node if found; add if no id specified,
	// DELETE  group/{id}/node/         ::Seq[EntityId]
	// GET     group/{id}/node/{nid}/   ::Node
	// DELETE  group/{id}/node/{nid}/   ::EntityId
	// GET     group/{id}/node/{nid}/telemetry/&+{bound}   :: TelemetrySeries
	// GET     group/{id}/node/{nid}/log/{file}/&+{bound}  :: LogSeries


	// GET    group/procedure/      :: Seq[ProcedureView]
	// GET    group/procedure/{id}  :: Group
	// POST   group/procedure/      :: Seq[Procedure] -> Seq[EntityId]
	// DELETE group/procedure/      :: Seq[EntityId]  // delete all procedure
	// DELETE group/procedure/{id}  :: EntityId       // delete specific procedure
	// POST   group/procedure/{id}/exec


	private val groups = HttpService[Task] {
		case GET -> Root / "groups" => Ok(groupRepo.findAll().map{_.asJson})
	}

	private val nodes = HttpService[Task] {
		case GET -> Root / "nodes" => ???
	}


	lazy val services: HttpService[Task] = groups <+> nodes


}
