package io.knact.guard.server


import java.nio.file.{Path, Paths}
import io.knact.guard._

import doobie._
import doobie.implicits._
// In order to evaluate tasks, we'll need a Scheduler
import monix.execution.Scheduler.Implicits.global

// A Future type that is also Cancelable
import monix.execution.CancelableFuture

// Task is in monix.eval
import monix.eval.Task
import cats._
import cats.effect._
import cats.implicits._

class H2JdbcContext extends ApiContext {

	// DONE insert nodes
	// DONE defining procedure table
	// DONE Create table uniquely on first call and keep
	// DONE Define methods for modifying and inspecting the database
	// DONE Decide how to store logs.


	val dbPath = Paths.get(".test").toAbsolutePath

	override def nodes: NodeRepository = new Nodes()
	override def procedures: ProcedureRepository = new Procedures()


	val xa = Transactor.fromDriverManager[Task](
		"org.h2.driver",
		s"jdbc:h2:$dbPath"
	)

	private val ddl = for {
    val nodes <-sql"""
                    IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='NODE')
                    BEGIN
                      CREATE TABLE NODES(
                      id BIGINT,
                      host VARCHAR NOT NULL,
                      port INT NOT NULL,
                      remark VARCHAR,
                      logLine BIGINT,
                      telemetry BIGINT,
                      PRIMARY KEY (id),
                      FOREIGN KEY (logLine) REFERENCES LOGS(lineID)
                      FOREIGN KEY (telemetry) REFERENCES TELEMETRY(id)
                      )
                    END
            """.update.run

		val procedures <- sql"""
                    IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='PROCEDURE')
                    BEGIN
                      CREATE TABLE PROCEDURES(
                      id BIGINT,
                      desc VARCHAR,
                      code VARCHAR,
                      duration BIGINT
                      )
                    END""".update.run // TODO: How best to stor duration

    val logs <- sql"""
                   IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='LOGS')
                   BEGIN
                      CREATE TABLE LOGS(
                      lineID BIGINT,
                      lineVal VARCHAR,
                      nodeID BIGINT,
                      PRIMARY KEY(lineID),
                      FOREIGN KEY (nodeID) REFERENCES NODE(id)
                      )
                   END   """.update.run

    val telemetry <- sql"""
                           IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA WHERE TABLE_NAME='TELEMETRY')
                           BEGIN
                              CREATE  TABLE TELEMETRY(
                              id BIGINT,
                              verdict VARCHAR,
                              PRIMARY KEY(id),
                              FOREIGN KEY (id) REFERENCES NODES(id)
                              )
                           END
                           """.update.run
	} yield(nodes, procedures, logs, telemetry)

	//println(ddl.transact(xa).unsafeRunSync)

  def upsertNode(node: Node): Task[Id[Node]] = {
    // Note: ID can be none
    val id, host, port, remark, logs  =
      node.id,
      node.target.host,
      node.target.port,
      node.remark,
      node.logs

    id match {
      case None =>
        for {
          id <- sql"""insert into nodes(id, host, port, remark, logs) values($host, $port, $remark, $logs)"""
          .update
          .withUniqueGeneratedKeys[Long] ("id").transact(xa)
        } yield (id)
      case _ =>
        for {
          _ <- sql"""update nodes set host=$host, port=$port, remark=$remark, logs=$logs where id=$id """.update.transact(xa)
        } yield(id)
    }
  }

  def upsertProcedure(proc: Procedure): Task[Id[Procedure]] =  {
    val id, desc, code, duration =
      proc.id,
      proc.desc,
      proc.code,
      proc.duration

    id match {
      case None =>
        for {
          id <- sql"""insert into procedures(desc, code, duration) values($desc, $code, $duration)"""
            .update
            .withUniqueGeneratedKeys[Long]("id").transact(xa)
        } yield (id)
      case _ =>
        for {
          _ <- sql"""update procedures set desc=$desc, code=$code, duration=$duration where id=$id """.update.transact(xa)
        } yield (id)
    }
  }

  def selectNodes():Task[Seq[(Long, String, Int, String, String)]] = {
    sql"select id, host, port, remark, logs from nodes".query[(Long, String, Int, String, String)].to[Seq].transact(xa)
  }

  def selectProcedures(): Task[Seq[Procedure]] = {
    sql"select id, desc, code, duration from procedures".query[Procedure].to[Seq].transact(xa)
  }

  class Procedures() extends(ProcedureRepository) = {
    def list(): Task[Seq[Id[Procedure]]] = {
      sql"select id from PROCEDURES".query[Id[Procedure]].toSeq.transact(xa)
    }

    def find(id: Id[Procedure]): Task[Option[Procedure]] ={
      sql"select id, desc, code, duration from PROCEDURES where id=%id".query[Option[Procedure]].transact(xa)
    }

    def delete(id:Id[Procedure]): Task[Id[Procedure]] = {
      sql"delete from procedures where id=%id".query[Option[Procedure]].transact(xa) //TODO Optional | Failure
    }

    def insert(p: Procedure) = Id[Procedure] = {
      upsertProcedure(p).asyncBoundary
    }

    def update(id: Id[Procedure], f: Procedure=>Procedure): Task[Id[Procedure]] = {
      upsertProcedure(id)
    }

  }

  class Nodes extends(NodeRepository) = {

    def list(): Task[Seq[Id[Node]]] = {
      sql"select id from nodes".query[Id[Node]].toSeq.transact(xa)
    }

    def find(id: Id[Node]): Task[Option[Node]] ={
      sql"select id, desc, code, duration from nodes where id=%id".query[Option[Node]].transact(xa)
    }

    def delete(id:Id[Node]): Task[Id[Node]] = {
      sql"delete from nodes where id=%id".query[Option[Node]].transact(xa) //TODO Optional | Failure
    }

    def insert(n: Node) = Id[Node] = {
      upsertNode(n).asyncBoundary
    }

    def update(id: Id[Node], f: Node=>Node): Task[Id[Node]] = {
      upsertNode(id)
    }

    def ids: Observable[Set[Id[Node]]] = {
      sql"select id from nodes".query[Id[Node]].toSet.transact(xa)
    }

    // TODO:What are the delta functions?
    // TODO: What are the persist functions?
    // TODO: What is the Execute function?

    def telemetries(nid: Id[Node]): Task[Option[TelemetrySeries]] = {
      sql"select verdict from telemetry where id=$nid".query[Option[TelemetrySeriies]].transact(xa)
    }

    def logs(nid: Id[Node]): Task[Option[LogSeries]] = {
      sql"select lineID, lineVal from logs where nodeID=%nid".query[Option[LogSeries]].transact(xa)
    }

  }






}
