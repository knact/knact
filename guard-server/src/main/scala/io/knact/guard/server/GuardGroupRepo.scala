package io.knact.guard.server

import io.knact.guard.guard
import io.knact.guard.guard.{Failure, GroupRepo}
import monix.eval.Task

import scala.collection.mutable.ArrayBuffer

class GuardGroupRepo extends GroupRepo{


  private val groups : ArrayBuffer[guard.Group] = ArrayBuffer()

  override def findAll(): Task[Seq[guard.Group]] = Task.pure(groups)
  override def findById(id: guard.EntityId): Task[Option[guard.Group]] = Task(groups.find{g => g.id == id})
  override def upsert(group: guard.Group): Task[Either[Failure, guard.Group]] = ???
  override def delete(id: guard.EntityId): Task[Either[Failure, guard.EntityId]] = ???

  override def mapToView(a: guard.Group): guard.GroupView = ???
}
