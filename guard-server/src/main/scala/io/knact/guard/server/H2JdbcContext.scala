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
	// TODO Decide how to store logs.
  // TODO Decide how to deal with telemetries

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
                      remark VARCHAR
                      )
                    END
            """.update.run

		val procedures <- sql"""
                    IF NOT EXISTS(SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='PROCEDURE')
                    BEGIN
                      CREATE TABLE PROCEDURES(
                      id BIGINT,
                      desc VARCHAR,
                      code VARCHAR
                      )
                    END""".update.run // TODO: How best to stor duration
	} yield(nodes, procedures)

	//println(ddl.transact(xa).unsafeRunSync)

  def upsertNode(node: Node): Update0 ={
    val id, host, port, remark  = node.id, node.target.host, node.target.port, node.remark
    sql"""if exists(select 1 from nodes where id=$id)
            begin
              update person set host=$host, port=$port, remark=$remark where id=$id
            end
          else
            begin
              insert into nodes (id, host, port, remark)
              values            ($id, $host, $port, $remark)
            end""".update
  }

  def upsertProcedure(proc: Procedure): Update0 = {
    val id, desc, code = proc.id, proc.description, proc.code
    sql"""if exists(select 1 from procedures where id=$id)
            begin
              update procedure set desc=$desc, code=$code where id=$id
            end
          else
            begin
              insert into procedures (desc, code)
              values                 ($id, $desc, $code)
            end""".update
  }

  def selectNodes():Query0[(Long, String, Int, String)] = {
    sql"select id, host, port, remark from nodes".query[(Long, String, Int, String)]
  }

  def selectProcedures(): Query0[(Long, String, String)] = {
    sql"select id, desc, code from procedures".query[(Long, String, String)]
  }





}
