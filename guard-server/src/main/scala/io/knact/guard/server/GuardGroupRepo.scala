package io.knact.guard.server

import io.knact.guard.guard
import io.knact.guard.guard.{Failure, GroupRepo, GroupView}
import monix.eval.Task

import scala.collection.mutable.ArrayBuffer

class GuardGroupRepo(groups: ArrayBuffer[guard.Group]) extends GroupRepo{

  override def findAll(): Task[Seq[guard.Group]] = Task.pure(groups)

  override def findById(id: guard.EntityId): Task[Option[guard.Group]] = Task(groups.find{g => g.id == id})

  override def upsert(group: guard.Group): Task[Either[Failure, guard.Group]] = Task {
    groups.find(g => g.id == group.id) match {
      case None => {
        group +: groups
        Right(group)
      }

      case g    => {
        groups.map(grp => if (grp == g) grp else g)
        Right(group)
      }
    }
  }

  override def delete(id: guard.EntityId): Task[Either[Failure, guard.EntityId]] = Task {
    groups.find(g => g.id == id) match {
      case None => Left(new Failure())
      case Some(g) => Right(g.id)
    }
  }

  override def mapToView(a: guard.Group): guard.GroupView = GroupView(id = a.id, name = a.name, nodes = a.nodes.map(node => node.id))
}