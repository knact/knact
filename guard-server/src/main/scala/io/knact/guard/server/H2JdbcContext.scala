package io.knact.guard.server


import java.nio.file.Paths
import java.time.{Duration, ZonedDateTime}

import doobie._
import doobie.implicits._
import doobie.util.log.Success
import io.knact.guard.Entity.{Id, LogSeries, Node, Procedure, SshKeyTarget, SshPasswordTarget, TargetGen, TelemetrySeries, TimeSeries}
import io.knact.guard._
import io.knact.guard.Telemetry.{Critical, DiskStat, Error, Iface, MemoryStat, NetStat, Offline, Ok, Online, Status, ThreadStat, Timeout, Verdict, Warning}
import monix.reactive.Observable
import squants.information.{Information, InformationUnit}
// In order to evaluate tasks, we'll need a Scheduler
import monix.execution.Scheduler.Implicits.global
// Task is in monix.eval
import monix.execution.CancelableFuture
import cats.implicits._
import monix.eval.Task
import scala.util.{Success, Failure}

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

  class Nodes extends NodeRepository {
    def findThreadStat(id: Id[Node]): Task[List[ThreadStat]] = {
      sql"""select running, sleeping, stopped, zombie from threadStat where id=$id"""
        .query[ThreadStat]
        .map(f => f).to[List].transact(xa)
    }
    def findMemStat   (id: Id[Node]): Task[List[MemoryStat]] = {
      sql"""select total, free, used, cache from memoryStat where id=$id"""
        .query[MemoryStat]
        .map(f => f).to[List].transact(xa)
    }

    def findNetStat   (id: Id[Node]): Task[List[Map[Iface, NetStat]]] = {
      sql"""select iface, mac, inet, bcast, mask, inet6, scope, tx1, rx1 from netStat where id=$id"""
        .query[(Iface, NetStat)]
        .map(f => Map((f._1, f._2)))
        .to[List].transact(xa)
    }

    def findDiskStat  (id: Id[Node]): Task[List[Map[Path, DiskStat]]] = {
      sql"""select path, freeVal, freeUnits from diskStat where id=$id"""
        .query[(Path, DiskStat)]
        .map(f => Map((f._1, f._2))).to[List].transact(xa)
    }

    def findTelemetry(id: Id[Node]): Task[List[Telemetry]] = {

      var memStat =    (); findMemStat(id).runAsync.foreach(f => {memStat = f.head})
      var threadStat = (); findThreadStat(id).runAsync.foreach(f => {threadStat = f.head})
      var netStat =    (); findNetStat(id).runAsync.foreach(f => {netStat = f.head})
      var diskStat =   (); findDiskStat(id).runAsync.foreach(f => {diskStat = f.head})
      sql"""select arch, duration, users, processorCount, loadAverage from telemetry where id=$id"""
        .query[(String, Duration, Long, Int, Double)]
        .map(f => {
          //TODO: Telemetry(f._1, f._2, f._3, f._4, f._5, findMemStat(id), findThreadStat(id), findNetStat(id), findDiskStat(id))
        })
        .to[List].transact(xa)
    }
    def findStatus(id : Id[Node]): Task[List[Option[Telemetry.Status]] = {
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
              val telemetry = findTelemetry(id)
              //TODO: Online(verdict, Option(f.reason), telemetry)
          }
        }))
        .to[List].transact(xa)
    }

    def findTarget(id: Id[Node]): Task[List[Entity.Target]] = {
      sql"""select targetHost, targetPort, targetUsername, targetKeyPath, targetPassword from nodes where id=$id"""
        .query[Entity.TargetGen]
        .map(f => {
          f.password match {
            case "" => SshKeyTarget(f.host, f.port, f.username, f.keyPath)
            case _  => SshPasswordTarget(f.host, f.port, f.username, f.password)
          }} )
        .to[List].transact(xa)
    }

    def findLogs(id : Id[Node]): Task[List[Map[Path, ByteSize]]] = {
      sql""" select path, byteSize from logs where id=$id"""
        .query[(Path, ByteSize)]
        .map(f => Map((f._1, f._2)))
        .to[List].transact(xa)
    }

    def list(): Task[Seq[Id[Node]]] = {
      sql"""
           |select id from nodes"""
        .query[Id[Node]].to[Seq].transact(xa)
    }

    def find(id: Id[Node]): Task[Option[Node]] = {

      val target = findTarget(id)
      val status = findStatus(id)
      val logs   = findLogs(id)
      sql"""
        | select id, remark from node where id=$id
        """
        .query[(Id[Node], String)]
        .map(f => {
          //TODO: Entity.Node(f._1, target, f._2, status, logs)
        })
        .to[Option].transact(xa)
    }

    def insert(n: Node): Task[Failure | Id[Node]] = ???
    def update(id: Id[Node]): Task[Failure | Id[Node]] = ???
    def delete(id: Id[Node], f: Node => Node): Task[Failure | Id[Node]] = ???

  }
}
