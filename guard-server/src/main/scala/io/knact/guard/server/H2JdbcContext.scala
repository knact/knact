package io.knact.guard.server


import java.nio.file.Paths
import java.time.{Duration, ZonedDateTime}

import doobie._
import doobie.implicits._
import io.knact.guard.Entity.{Id, LogSeries, Node, Procedure, SshKeyTarget, SshPasswordTarget, TargetGen, TelemetrySeries, TimeSeries}
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
           |mac VARCHAR,
           |inet LONGVARCHAR,
           |bcast LONGVARCHAR,
           |mask  LONGVARCHAR,
           |inet6 LONGVARCHAR,
           |scope LONGVARCHAR,
           |tx1    BIGINT,
           |rx1    BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    memoryStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS MEMORYSTAT(
           |id BIGINT,
           |total BIGINT,
           |free  BIGINT,
           |used  BIGINT,
           |cache BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    diskStat <-
      sql"""
           |CREATE TABLE IF NOT EXISTS DISKSTAT(
           |id BIGINT,
           |path VARCHAR,
           |freeVal BIGINT,
           |freeUnits BIGINT,
           |PRIMARY KEY (id)
           |);""".update.run


    telemetry <-
      sql"""
           |CREATE TABLE IF NOT EXISTS TELEMETRY(
           |id BIGINT,
           |arch VARCHAR,
           |duration BIGINT,
           |users BIGINT,
           |processorCount BIGINT,
           |loadAverage    BIGINT,
           |PRIMARY KEY (id),
           |);""".update.run

    status <-
      sql"""
           | CREATE TABLE IF NOT EXISTS STATUS(
           | id BIGINT,
           | statMessage VARCHAR,
           | error VARCHAR,
           | verdict VARCHAR,
           | reason VARCHAR,
           | PRIMARY KEY(id),
           | );""".update.run


    nodes <-
      sql"""
           |CREATE TABLE IF NOT EXISTS NODES(
           |id BIGINT,
           |targetHost VARCHAR,
           |targetPort BIGINT,
           |targetUsername VARCHAR,
           |targetKeyPath VARCHAR,
           |targetPassword VARCHAR,
           |remark VARCHAR,
           |PRIMARY KEY (id),
           |);""".update.run
      // Note passwords stored in plaintext because I'm a bad programmer
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
  implicit def verdictMeta : Meta[Verdict] = Meta[String].xmap(Verdict.fromString(_), _.toString )
  object Verdict{
    def fromString(s:String) : Telemetry.Verdict = {
      s match {
        case "Ok"       => Telemetry.Ok
        case "Warning"  => Telemetry.Warning
        case "Critical" => Telemetry.Critical
      }
    }
  }

  /** Nodes **/
  def upsertNode(node: Node): Task[Id[Node]] = {
    // Note: ID can be none
    val Node(idOp, target, remark, status, logs) = node
    val target_host = target.host
    val target_port = target.port
    var targetUsername = target.username
    var targetKeyPath  = ""
    var targetPassword = "" // Real bad. REALLY BAD
    target match{
      case t: SshKeyTarget =>
        targetKeyPath  = t.keyPath
      case t: SshPasswordTarget =>
        targetPassword = t.password
    }

    val stat = status.getOrElse(None) match {
      case onl: Online  => onl
      case err: Error   => err
      case None         => None
      case _            => status.toString
    }



    logs.map( l =>{
      val p = l._1
      val b = l._2
      sql"""
           | MERGE INTO LOGS KEY(id) VALUES($idOp, $p, $b)
         """.update.run
    })

    stat match {
      case None => None

      case s : String => sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, $s, NULL, NULL, NULL)""".update.run

      case err: Error =>
        val err_string = err.error;

        sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, 'Error', $err_string, NULL, NULL);""".update.run

      case onl : Online => {
        val Online(verdict, reason, telemetry) = onl
        val Telemetry(arch, uptime, users,
                      processorCount, loadAverage, memoryStat,
                      threadStat, netStats, diskStats) = telemetry
        // Note netStats and diskStats are Map[String, T]
        val ThreadStat(running, sleeping, stopped, zombie) = threadStat
        sql"""|MERGE INTO THREADSTAT KEY(id) VALUES ($idOp, $running, $sleeping, $stopped, $zombie);""".update.run

        val MemoryStat(total, free, used, cached) = memoryStat
        sql"""|MERGE INTO MEMORYSTAT KEY(id) VALUES ($idOp, $total, $free, $used, $cached);""".update.run

        netStats.map(m =>{
          val iface = m._1
          val NetStat(mac: String,
            inet: String,
            bcast: String,
            mask: String,
            inet6: Option[String],
            scope: String, tx: Information, rx: Information) = m._2
          sql""" | MERGE INTO NETSTAT KEY(id) VALUES ( $idOp, $iface, $mac, $inet, $bcast, $mask, $inet6, $scope, $tx, $rx);""".update.run})

        diskStats.map(m =>{
          val path = m._1
          val DiskStat(free, used) = m._2
          sql"""|MERGE INTO DISKSTAT KEY(id) values ($idOp, $path, $free, $used)""".update.run
        })


        sql"""|MERGE INTO TELEMETRY KEY(id) VALUES ($idOp, $arch, $uptime, $users, $processorCount, $loadAverage);""".update.run

        sql"""|MERGE INTO STATUS KEY(id) VALUES ($idOp, 'Online', NULL, $verdict, $reason);""".update.run
      }
    }


    sql"""
         | MERGE INTO NODES KEY(id) VALUES($idOp, $target_host, $target_port, $targetUsername, $targetKeyPath, $targetPassword, $remark);
    """.update.withUniqueGeneratedKeys[Id[Node]]("id").transact(xa)

  }

  implicit def nodeMeta: Meta[Node] = Meta[(Long, String)].xmap(Node.fromLegacy, _.toLegacy)
  object Node {
    def fromLegacy(l : (Long, String)): Entity.Node = {
      Node(l._1, None, l._2, None, None )
    }
  }

  implicit def targetGenMeta : Meta[TargetGen] = Meta[(String, Int, String, String, String)].xmap(TargetGen.fromLegacy, _.toLegacy)
  object TargetGen{
    def fromLegacy(l : (String, Int, String, String, String)) : TargetGen = {
      TargetGen(l._1, l._2, l._3, l._4, l._5)
    }
  }
  // TODO: Meta[Telemetry], Meta[Stat],
  implicit def statusGenMeta : Meta[Telemetry.StatusGen] = Meta[(String, String, String, String)].xmap(StatusGen.fromLegacy, _.toLegacy)
  object StatusGen {
    def fromLegacy(l : (String, String, String, String)) : Telemetry.StatusGen = {
      StatusGen(l._1, l._2, l._3, l._4)
    }
  }

  class Nodes extends NodeRepository {



    def findMemStat   (id: Id[Node]): MemoryStat          = ???
    def findThreadStat(id: Id[Node]): ThreadStat          = ???
    def findNetStat   (id: Id[Node]): Map[Iface, NetStat] = ???
    def findDiskStat  (id: Id[Node]): Map[Path, DiskStat] = ???

    def findTelemetry(id: Id[Node]):Telemetry = {
      sql"""select arch, duration, users, processorCount, loadAverage from telemetry where id=$id"""
        .query[Telemetry]
        .map(f => f)
        .unique.run
    }
    def findStatus(id : Id[Node]): Option[Telemetry.Status] = {
      sql"""select statMessage, error, verdict, reason from status where id=$id"""
        .query[Telemetry.StatusGen]
        .map(f =>Option( {
          f.statMessage match {
            case "Offline" => Offline
            case "Timeout" => Timeout
            case "Error"   => Error(f.error)
            case "Online"  =>
              val verdict = f.verdict match {
                case "Ok"       =>  Ok
                case "Warning"  => Warning
                case "Critical" => Critical
              }
              Online(verdict, Option(f.reason), findTelemetry(id))
          }
        }))
        .unique.run
    }

    def findTarget(id: Id[Node]): Entity.Target = {
      sql"""select targetHost, targetPort, targetUsername, targetKeyPath, targetPassword from nodes where id=$id"""
        .query[TargetGen]
        .map(f => {
          f.password match {
            case "" => SshKeyTarget(f.host, f.port, f.username, f.keyPath)
            case _  => SshPasswordTarget(f.host, f.port, f.username, f.password)
          }} )
        .unique.run
    }

    def list(): Task[Seq[Id[Node]]] = {
      sql"""
           |select id from nodes"""
        .query[Id[Node]].to[Seq].transact(xa)
    }

    def find(id: Id[Node]): Task[Option[Node]] = {

      val target:Entity.Target = findTarget(id)
      val status: Option[Status] = ???

      sql"""
        | select id, remark from node where id=$id
        """.query[Node].map(f => {

      })
    }

  }
}
