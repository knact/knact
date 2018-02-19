package io.knact.guard.server

import java.nio.file.{Path, Paths}

import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.implicits._
import doobie._
import cats._
import cats.effect._
import cats.implicits._
import fs2.Stream
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._


import io.knact.guard._

class H2JdbcContext extends ApiContext {

	// TODO write me
	// TODO insert nodes, insert groups
	// TODO defining procedure table
	// TODO Create table uniquely on first call and keep
	// TODO Define methods for modifying and inspecting the database

	val dbPath = Paths.get(".test").toAbsolutePath

	override def groups: GroupRepository = ???
	override def nodes: NodeRepository = ???
	override def procedures: ProcedureRepository = ???


	val xa = Transactor.fromDriverManager[Task](
		"org.h2.driver",
		s"jdbc:h2:$dbPath"
	)

	private val ddl = for {
		nodes <-sql"""CREATE TABLE NODE(
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          groupID BIGINT NOT NULL, FOREIGN KEY (groupID) REFERENCES public.GROUP(id),
          host VARCHAR NOT NULL,
          port INT NOT NULL,
          remark VARCHAR
          // telemetries
          //logs
          )"""
		groups <- sql"""CREATE TABLE GROUP(
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR NOT NULL,
      nodes BIGINT,
      FOREIGN KEY (nodes) REFERENCES public.NODE(id)
    )"""

		procedures <- sql"""CREATE TABLE PROCEDURE(
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      description VARCHAR,
      code VARCHAR
      //timeout Duration?

    )"""
	} yield(nodes, groups, procedures)

	println(ddl.transact(xa).unsafeRunSync)


}
