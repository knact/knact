package io.knact.guard.server

import java.util.concurrent.atomic.AtomicLong

import io.knact.guard._
import monix.eval.Task

import scala.collection.mutable.ArrayBuffer

class GuardGroupRepo(private val groups: ArrayBuffer[Group] = ArrayBuffer()) extends GroupRepo {

	private final val counter: AtomicLong = new AtomicLong(0)


	override def findAll(): Task[Seq[Group]] = Task.pure(groups)

	override def findById(id: Id[Group]): Task[Option[Group]] =
		Task(groups.find {_.id == id})

	override def upsert(group: Group): Task[Either[Failure, Group]] = Task {
		groups.indexWhere {_.id == group.id} match {
			case -1  =>
				val indexed = group.copy(id = tag(counter.getAndIncrement()))
				groups += indexed
				Right(indexed)
			case idx =>
				groups.update(idx, group)
				Right(group)
		}
	}

	override def delete(id: Id[Group]): Task[Either[Failure, Id[Group]]] = Task {
		groups.find(_.id == id) match {
			case None    => Left(s"group with ID = $id does not exist")
			case Some(g) => {
				groups -= g
				Right(g.id)
			}
		}
	}

	override def mapToView(that: Group): GroupView = GroupView(
		id = that.id,
		name = that.name,
		nodes = that.nodes.map {_.id})

	override def deleteAll() : Task[Either[Failure, Unit]] =
		Task(Right(groups.drop(counter.getAndSet(0).toInt)))
}