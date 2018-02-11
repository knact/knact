package io.knact.guard.server

import cats.syntax._
import cats._
import cats.implicits._
import org.http4s.dsl.Http4sDsl
//import cats._
import cats.{Group => CGroup, _}
import monix.eval.Task
import monix.eval.instances._
import org.http4s.HttpService
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.knact.guard._

import io.circe.java8.time._


class APIService(val groupRepo: GuardGroupRepo) extends Http4sDsl[Task] {




	// GET     group          :: Seq[GroupView]
	// GET     group/{id}     :: Group
	// POST    group	      :: Seq[Group] -> Seq[EntityId]  // update a group if found; add if no id specified,
	// DELETE  group          :: Seq[EntityId] // delete all groups
	// DELETE  group/{id}     :: EntityId  // delete specific group
	private val groups = HttpService[Task] {
		case GET -> Root / "groups" => Ok(groupRepo.findAll().map{_.asJson})
	}

	private val nodes = HttpService[Task] {
		case GET -> Root / "nodes" => ???
	}


	lazy val services: HttpService[Task] = groups <+> nodes


}
