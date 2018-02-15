package io.knact.guard.server

import shapeless.tag
import cats.effect.IO
import cats.implicits._
import io.circe.Json
import org.http4s.dsl.Http4sDsl
import monix.eval.Task
import org.http4s.HttpService
import org.http4s._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.knact.guard._
import org.http4s.dsl.io._
import io.circe.java8.time._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.java8.time._
import io.knact.guard.Entity.id
import io.knact.guard._
import monix.eval.Task.{Async, Error, Eval, FlatMap, Map, MemoizeSuspend, Now, Suspend}

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.java8.time._
import io.knact.guard.Entity.id
import io.knact.guard._
import org.http4s.circe._


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

//  implicit val groupDecoder = jsonOf[Task, Group]

  private val groups = HttpService[Task] {
    case GET -> Root / "groups" => Ok(groupRepo.findAll().map {
      _.asJson
    })
//    case GET -> Root / "groups" / IntVar(id) => Ok(groupRepo.findById(groupRepo.tag(id)).asJson)
    case request@POST -> Root / "groups" =>

      val decoded = request.as[Json].map { v => v.as[Group] }

     val thing =  for {
        result <- decoded
        outcome <- result match {
                    case Left(failed) => Task.pure(Left(failed.message))
                    case Right(success) => groupRepo.upsert(success)
                    }
      } yield outcome

     thing.map {
       case Left(value) => value.asJson
       case Right(value) => value.asJson
     }.flatMap{ v => Ok(v)}

    //			Ok(groupRepo.upsert(group))
//    case DELETE -> Root / "groups" => Ok(groupRepo.deleteAll())
//    case DELETE -> Root / "groups" / IntVar(id) => Ok(groupRepo.delete(groupRepo.tag(id)))
  }

  private val nodes = HttpService[Task] {
    case GET -> Root / "nodes" => Ok()
  }


  lazy val services: HttpService[Task] = groups <+> nodes

}
