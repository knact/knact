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
  // TOOD: Smart functions for data manipulation i.e joins etc
  

	val dbPath = Paths.get(".test").toAbsolutePath

	override def nodes: NodeRepository = ???
	override def procedures: ProcedureRepository = ???


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
                      logs VARCHAR(MAX)
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
	} yield(nodes, procedures)

	//println(ddl.transact(xa).unsafeRunSync)

  def upsertNode(node: Node) = {
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
          .withUniqueGeneratedKeys[Long] ("id")
        } yield (id)
      case _ =>
        for {
          _ <- sql"""update nodes set host=$host, port=$port, remark=$remark, logs=$logs where id=$id """
        } yield()
    }
  }

  def upsertProcedure(proc: Procedure) = {
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
            .withUniqueGeneratedKeys[Long]("id")
        } yield (id)
      case _ =>
        for {
          _ <- sql"""update procedures set desc=$desc, code=$code, duration=$duration where id=$id """.update
        } yield ()
    }
  }

  def selectNodes():Query0[(Long, String, Int, String, String)] = {
    sql"select id, host, port, remark, logs from nodes".query[(Long, String, Int, String, String)].to[List].transact(xa).unsafeRunSync
  }

  def selectProcedures(): List[(Long, String, String, Long)] = {
    sql"select id, desc, code, duration from procedures".query[(Long, String, String, Long)].to[List].transact(xa).unsafeRunSync
  }





}
