package io.knact.guard.server


import java.nio.file.Paths
import java.time.{Duration, ZonedDateTime}

import doobie._
import doobie.implicits._
import io.knact.guard.Entity.{Id, LogSeries, Node, Procedure, SshKeyTarget, SshPasswordTarget, TelemetrySeries, TimeSeries}
import io.knact.guard._
import io.knact.guard.Telemetry.{Critical, DiskStat, Error, Iface, MemoryStat, NetStat, Offline, Ok, Online, Status, ThreadStat, Timeout, Verdict, Warning}
import monix.reactive.Observable
import squants.information.{Information, InformationUnit}
// In order to evaluate tasks, we'll need a Scheduler
import monix.execution.Scheduler.Implicits.global
// Task is in monix.eval
import cats.implicits._
import monix.eval.Task

class H2JdbcContext extends ApiContext {

  //DONE: Telemetry base
  //TODO: Entitity mappings
  //TODO: Relevant entitity and SQL commands
  override def version: String = ???

  override def repos: (NodeRepository, ProcedureRepository) = ???

  override def startTime: ZonedDateTime = ???


  private val dbPath = Paths.get("mem:test").toAbsolutePath

  override def nodes: NodeRepository = ??? //new Nodes()
  override def procedures: ProcedureRepository = ??? //new Procedures()


  val xa = Transactor.fromDriverManager[Task](
    "org.h2.driver",
    s"jdbc:h2:$dbPath"
  )

  private val ddl = for {
    logs <-
      sql"""
           |CREATE TABLE IF NOT EXISTS LOGS(
           |id BIGINT,
           |path VARCHAR,
           |byteSize BIGINT,
           |PRIMARY KEY(id),
           |);""".update.run

    threadStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS THREADSTAT(
           |id BIGINT,
           |running BIGINT,
           |sleeping BIGINT,
           |stopped BIGINT,
           |zombie  BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    netStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS NETSTAT(
           |id BIGINT,
           |iface VARCHAR,
           |inet LONGVARCHAR,
           |bcast LONGVARCHAR,
           |mask  LONGVARCHAR,
           |inet6 LONGVARCHAR,
           |scope LONGVARCHAR,
           |tx1    BIGINT,
           |tx2    BIGINT,
           |rx1    BIGINT,
           |rx2    BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    memoryStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS MEMORYSTAT(
           |id BIGINT,
           |totalVal BIGINT,
           |totalUnit BIGINT,
           |freeVal  BIGINT,
           |freeUnit BIGINT,
           |usedVal BIGINT,
           |usedUnit BIGINT,
           |cacheVal BIGINT,
           |cacheUnit BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    diskStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS DISKSTAT(
           |id BIGINT,
           |freeVal BIGINT,
           |freeUnits BIGINT,
           |PRIMARY KEY (id)
           |);""".update.run


    telemetry <-
      sql"""
           |CREATE TABLE IF NOT EXISTS TELEMETRY(
           |id BIGINT,
           |arch VARCHAR,
           |users BIGINT,
           |processorCount BIGINT,
           |loadAverage    BIGINT,
           |memoryStatId   BIGINT,
           |threadStatId   BIGINT,
           |ifaceToNetStat VARCHAR,
           |pathToDiskStat VARCHAR,
           |PRIMARY KEY (id),
           |FOREIGN KEY (memoryStatId) REFERENCES MEMORYSTAT(id),
           |FOREIGN KEY (threadStatId) REFERENCES THREADSTAT(id),
           |FOREIGN KEY (ifaceToNetStat) REFERENCES NETSTAT(iface),
           |FOREIGN KEY (pathToDiskStat) REFERENCES MEMORYSTAT(id)
           |);""".update.run

    status <-
      sql"""
           | CREATE TABLE IF NOT EXISTS STATUS(
           | id BIGINT,
           | statMessage VARCHAR,
           | error VARCHAR,
           | verdict VARCHAR,
           | reason VARCHAR,
           | telemetry BIGINT,
           | PRIMARY KEY(id),
           | FOREIGN KEY (telemetry) REFERENCES TELEMETRY(id)
           | );""".update.run


    nodes <-
      sql"""
           |CREATE TABLE IF NOT EXISTS NODES(
           |id BIGINT,
           |targetHost VARCHAR,
           |targetPort BIGINT,
           |remark VARCHAR,
           |PRIMARY KEY (id),
           |);""".update.run

    procedures <-
      sql"""
           |CREATE TABLE IF NOT EXISTS PROCEDURES(
           |id BIGINT,
           |name VARCHAR,
           |remark VARCHAR,
           |code VARCHAR,
           |timeout BIGINT,
           |PRIMARY KEY (id)
           |);""".update.run

  } yield (nodes, procedures, logs, telemetry, threadStat, netStat, memoryStat, diskStat,  status)




  /** Procedures **/


  implicit def nodeIdMeta: Meta[Id[Node]] = Meta[Long].xmap(Entity.id(_), _.toLong)
  implicit def procedureIdMeta: Meta[Id[Procedure]] = Meta[Long].xmap(Entity.id(_), _.toLong)
  implicit def durationMeta: Meta[Duration] = Meta[Long].xmap(Duration.ofHours(_), _.toHours)

  def upsertProcedure(proc: Procedure): Task[Id[Procedure]] = {
    val Procedure(procId, name, remark, code, timeout) = proc
    sql"""
          | MERGE INTO PROCEDURES KEY(id) VALUES($procId, $name, $remark, $code, $timeout
          |);""".update.withUniqueGeneratedKeys[Id[Procedure]]("id").transact(xa)
  }

  def selectProcedures(): Task[Seq[Procedure]] = {
    sql"select id, name, remark, code, timeout from procedures".
      query[Procedure].
      to[Seq].transact(xa)
  }

  class Procedures  extends ProcedureRepository {

    def list(): Task[Seq[Entity.Id[Procedure]]] = {
      sql"select * from PROCEDURES"
        .query[Procedure]
        .to[Seq].map(p => Seq(p.head.id))
        .transact(xa)
    }

    def find(id: Entity.Id[Procedure]): Task[Option[Procedure]] = {
      sql"select 1 from PROCEDURES where id=$id"
        .query[Procedure]
        .to[List].map(p => Option(p.head))
        .transact(xa)
    }

		def delete(id: Entity.Id[Procedure]): Task[ Failure | Entity.Id[Procedure]] = {
			sql"delete from procedures where id=$id"
				.query[Procedure]
        .to[List].map( x => Right(x.head.id))
				.transact(xa)
		}

    def insert(p: Procedure): Task[ Failure | Entity.Id[Procedure]] = {
      upsertProcedure(p).map(f => Right(f))
    }

    def update(id: Id[Procedure], f: Procedure => Procedure): Task[ Failure | Id[Procedure]] = {
      sql"""select * from PROCEDURES where id=$id"""
        .query[Procedure]
        .to[List]
        .map(p => Right((f (p.head)).id)).transact(xa)
    }

	}

  implicit def infoMeta  : Meta[Information] = Meta[Double].xmap(Information.apply(_).get, _.toBytes)
  implicit def optionMeta: Meta[Option[String]] = Meta[String].xmap(Option.apply(_), _.get)
  implicit def nodeMeta  : Meta[Id[Node]] = Meta[Long].xmap(Entity.id(_), _.toLong)
  /** Nodes **/
  def upsertNode(node: Node): Task[Id[Node]] = {
    // Note: ID can be none
    val Node(idOp, target, remark, status, logs) = node
    val target_host = target.host
    val target_port = target.port
    var diskStat, netStat, memoryStat, threadStat = ()
    val stat = status.getOrElse(None) match {
      case onl: Online  =>
        diskStat   = onl.telemetry.diskStats
        netStat    = onl.telemetry.netStat
        memoryStat = onl.telemetry.memoryStat
        threadStat = onl.telemetry.threadStat
        onl.telemetry

      case err: Error   => err
      case None         => None
      case _            => status.toString
    }


    for{

      _ <- logs.foreach( l =>{
        val p = l._1
        val b = l._2
        sql"""
             | MERGE INTO LOGS KEY(id) VALUES($idOp, $p, $b)
           """.update.run
      })

      _ <- stat match {
        case None => None
        case s : String => sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, $s, NULL, NULL, NULL, NULL)""".update.run
        case err: Error => val err_string = err.error; sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, 'Error'. $err_string, NULL, NULL, NULL)""".update.run
//        case onl : Online => {
//          // Telemetry
//
//        }
      }


      ret <- sql"""
           | MERGE INTO NODES KEY(id) VALUES($idOp, $target_host, $target_port, $remark);
      """.update.withUniqueGeneratedKeys[Id[Node]]("id").transact(xa)


    } yield ret
  }


  def selectNodes(): Task[Seq[Node]] = {
    sql"select * from nodes"
      .query[Node]
      .to[Seq].transact(xa)
  }

  class Nodes extends NodeRepository {

    def list(): Task[Seq[Id[Node]]] = {
      sql"select id from nodes"
        .query[Node]
        .to[Seq].map(p => Seq(p.head.id))
        .transact(xa)
    }

    def find(id: Entity.Id[Node]): Task[Option[Node]] = {
      sql"select id, desc, code, duration from nodes where id=$id"
        .query[Node]
        .to[Option].transact(xa)
    }

    def delete(id: Id[Entity.Node]): Task[Entity.Id[Node]] = {
      sql"delete from nodes where id=$id"
        .query[Node]
        .to[Entity.Id].transact(xa)
    }

    def insert(n: Node): Entity.Id[Node] = {
      upsertNode(Entity(n)).to[List].head
    }

    def update(id: Id[Node], f: Node => Node): Task[Id[Node]] = {
      upsertNode(id)
    }

    def ids: Observable[Set[Id[Node]]] = {
      sql"select id from nodes"
        .query[Id[Node]]
        .to[Set].transact(xa).to[Observable]
    }
  //
  //		def telemetries(nid: Id[Node]): Task[Option[TelemetrySeries]] = {
  //			val n = nid.toLong
  //			sql"select verdict from telemetry where id=$n"
  //				.query[TelemetrySeries]
  //				.to[Option].transact(xa)
  //		}
  //
  //		def logs(nid: Id[Node]): Task[Option[LogSeries]] = {
  //			val n = nid.toLong
  //			sql"select lineID, lineVal from logs where nodeID=$n"
  //				.query[LogSeries]
  //				.to[Option].transact(xa)
  //		}
  //
  //		//TODO: def meta(nid: Id[Entity.Node]): Task[Option[Node]]
  //		//TODO: def telemetryDelta: Observable[Id[Entity.Node]]
  //		//TODO: def logDelta: Observable[Id[Entity.Node]]
  //		/*TODO:
  //		def entities: Observable[Set[Node]] = ids
  //			.switchMap { ns => Observable.fromTask(Task.traverse(ns)(find)) }
  //			.map {_.flatten.toSet}
  //
  //		def persist(nid: Id[Entity.Node],
  //					time: ZonedDateTime,
  //					status: Status): Task[Failure | Id[Entity.Node]]
  //		def persist(nid: Id[Entity.Node],
  //					time: ZonedDateTime,
  //					path: Path,
  //					lines: Seq[Line]): Task[Failure | Id[Entity.Node]]
  //			*/
  //
  //
  //	}




}
