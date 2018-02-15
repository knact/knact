package io.knact.guard.server

import java.util.concurrent.atomic.AtomicLong

import io.knact.Basic.{NetAddress, PasswordCredential}
import io.knact.guard._
import monix.eval.Task
import monix.reactive.subjects.Subject

import scala.collection.mutable.ArrayBuffer

class GuardNodeRepo(private val nodes: ArrayBuffer[Node]) extends NodeRepo {

  private final val counter: AtomicLong = new AtomicLong(0)

  override def findAll(): Task[Seq[Node]] = Task.pure(nodes) //why not Task(nodes) ???

  override def findById(id: Id[Node]): Task[Option[Node]] =
    Task(nodes.find {
      _.id == id
    })

  override def upsert(node: Node): Task[Either[Failure, Node]] = Task {
    nodes.indexWhere {
      _.id == node.id
    } match {
      case -1 =>
        val indexed = node.copy(id = tag(counter.getAndIncrement()))
        nodes += indexed
        Right(indexed)
      case idx =>
        nodes.update(idx, node)
        Right(node)
    }
  }

  override def delete(id: Id[Node]): Task[Either[Failure, Id[Node]]] = Task {
    nodes.find(_.id == id) match {
      case None => Left(s"node with ID = $id does not exist")
      case Some(n) => {
        nodes -= n
        Right(n.id)
      }
    }
  }

  override def mapToView(that: Node): NodeView = {
    val (failed, success) = that.telemetries.values.partition(_.isLeft)
    NodeView(
      id = that.id,
      subject = ???,
      logs = that.logs.mapValues { v => v.values.flatten.map(_.length).sum },
      telemetry = TelemetrySummary(failed.size, success.size),
      remark = "hello node")
  }

  override def deleteAll(): Task[Either[Failure, Unit]] =
    Task(Right(nodes.drop(counter.getAndSet(0).toInt)))

  override def telemetries(id: Id[Node])(bound: Bound): Task[Option[TelemetrySeries]] = Task { //need to implement bounds as well
    nodes.find(_.id == id) match {
      case None => None
      case Some(node) => Some(node.telemetries)
    }
  }

  override def logs(id: Id[Node])(path: Path)(bound: Bound): Task[Option[LogSeries]] = Task { //need to implement bounds as well
    nodes.find(_.id == id) match {
      case None => None
      case Some(node) => node.logs.get(path)
    }
  }

  override def execute(id: Id[Node])(procedureId: Id[Procedure]): Task[Option[String]] = Task { //how to access procedures?
    nodes.find(_.id == id) match {
      case None => None
      case Some(node) => ???
    }
  }

}