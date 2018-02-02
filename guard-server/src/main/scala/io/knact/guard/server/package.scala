package io.knact.guard

import org.http4s._, org.http4s.dsl._
import org.http4s.HttpService
import org.http4s.dsl.{Root, _}
import cats._
import cats.implicits._

package object server {



//	// TODO
//	final val group = HttpService {
//		case GET -> Root / "group" / groupId =>
//			??? //Ok(s" ")
//	}
//
//	// TODO
//	final val node = HttpService {
//		case GET -> Root / "group" / groupId / "node" / nodeId =>
//			??? // Ok(s" ")
//	}



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




//	final val services = group |+| node

}
