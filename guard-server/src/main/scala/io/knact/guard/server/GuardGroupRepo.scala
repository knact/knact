package io.knact.guard.server

import io.knact.guard.guard
import io.knact.guard.guard.{Failure, GroupRepo, GroupView}
import monix.eval.Task

import scala.collection.mutable.ArrayBuffer

class GuardGroupRepo(private val groups: ArrayBuffer[guard.Group] = ArrayBuffer()) extends GroupRepo {

	override def findAll(): Task[Seq[guard.Group]] = Task.pure(groups)

	override def findById(id: guard.EntityId): Task[Option[guard.Group]] =
		Task(groups.find {_.id == id})

	override def upsert(group: guard.Group): Task[Either[Failure, guard.Group]] = Task {
		groups.indexWhere {_.id == group.id} match {
			case -1  =>
				groups += group
				Right(group)
			case idx =>
				groups.update(idx, group)
				Right(group)
		}
	}

	override def delete(id: guard.EntityId): Task[Either[Failure, guard.EntityId]] = Task {
		groups.find(_.id == id) match {
			case None    => Left(s"group with $id does not exist")
			case Some(g) => Right(g.id)
		}
	}

	override def mapToView(that: guard.Group): guard.GroupView = GroupView(
		id = that.id,
		name = that.name,
		nodes = that.nodes.map {_.id})
}