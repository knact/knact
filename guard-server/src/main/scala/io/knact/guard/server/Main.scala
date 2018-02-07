package io.knact.guard.server

import fs2.{Stream, Task}
import io.knact.guard.guard._
import org.http4s.server.blaze._
import org.http4s.util.StreamApp
import org.http4s._
import org.http4s.dsl._

import scala.collection.mutable.ArrayBuffer

object Main extends StreamApp {
	override def stream(args: List[String]): Stream[Task, Nothing] = {




		val repo = new GuardGroupRepo()



		var groups : Seq[GroupView] = new groupSeq

		def containedInGroups(str: String) : Boolean =
			isIntegral(str) && groups.isDefinedAt(Integer.parseInt(str))

		def containedInGroupNodes(groupId: String, nodeId: String) : Boolean =
			containedInGroups(groupId) &&
			isIntegral(nodeId) &&
			groups(Integer.parseInt(groupId)).nodes.isDefinedAt(Integer.parseInt(nodeId))


		def isIntegral(str: String): Boolean = str.forall(_.isDigit)

		val groupService = HttpService {

			case GET -> Root =>
				Ok(				repo.findAll())
//				if (groups.isEmpty)
//					BadRequest()
//				else
//					Ok(groups.toString())

			case GET -> Root / id =>
				if (containedInGroups(id))
					Ok(groups(Integer.parseInt(id)).toString())
				else
					BadRequest()

			//case request @ POST -> Root =>

			case DELETE -> Root => {
				groups = new groupSeq
				Ok()
			}

			case DELETE -> Root / id => {
				if (containedInGroups(id)) {
					groups = groups.filter(elem => groups.indexOf(elem) != Integer.parseInt(id))
					Ok()
				}
				else
					BadRequest()
			}

		}

		val nodeService = HttpService {

			case GET -> Root / groupId / "node" => {
				if (containedInGroups(groupId))
					Ok(groups(Integer.parseInt(groupId)).nodes.toString())
				else
					BadRequest()
			}

			case GET -> Root / groupId / "node" / nodeId => {
				if (containedInGroupNodes(groupId, nodeId))
					Ok("group id: " + groupId + "\nnode id: " + nodeId)
				else
					BadRequest()
			}
		}


		BlazeBuilder
			.bindHttp(8080, "localhost")
			.mountService(groupService, "/group")
			.mountService(nodeService, "/group")
			.serve
	}

}



class groupSeq extends Seq[GroupView] {
	override def apply(idx: Int) = this.toList.apply(idx)

	override def length = this.toList.length

	override def iterator = new groupSeqIterator(this)
}

class groupSeqIterator(obj: groupSeq) extends Iterator[GroupView] {
	var i = 0

	override def hasNext =
		if (obj.toList.length > i)
			true
		else
			false

	override def next = {
		i = i + 1
		obj(i - 1)
	}

}