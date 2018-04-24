package io.knact.guard.server


import java.nio.file.Paths
import java.time.{Duration, ZonedDateTime}

import doobie._
import doobie.implicits._
import doobie.util.log.Success
import io.knact.guard.Entity.{Id, LogSeries, Node, Procedure, SshKeyTarget, SshPasswordTarget, Target, TargetGen, TelemetrySeries, TimeSeries}
import io.knact.guard.{Failure, _}
import io.knact.guard.Telemetry.{Critical, DiskStat, Error, Iface, CpuStat, MemoryStat, NetStat, Offline, Ok, Online, Status, ThreadStat, Timeout, Verdict, Warning}
import monix.reactive.Observable
import squants.information.{Information, InformationUnit}

import scala.collection.immutable.TreeMap
// In order to evaluate tasks, we'll need a Scheduler
//import monix.execution.Scheduler.Implicits.global
//import monix.execution.CancelableFuture
// Task is in monix.eval
import cats.implicits._
import monix.eval.Task

class H2JdbcContext extends ApiContext {

	override def version: String = "1.1"

	override def repos: (NodeRepository, ProcedureRepository) = (new Nodes, new Procedures)

	override def startTime: ZonedDateTime = ZonedDateTime.now()


	private val dbPath = Paths.get(".test").toAbsolutePath

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

	} yield (nodes, procedures, logs, telemetry, threadStat, netStat, memoryStat, diskStat, status)


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

	class Procedures extends ProcedureRepository {

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

		def delete(id: Entity.Id[Procedure]): Task[Failure | Entity.Id[Procedure]] = {
			find(id).attempt.map(f => f match {
				case l: Left[Throwable, Option[Procedure]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Procedure]] =>
					r.value.getOrElse(None) match {
						case None         => Left("No procedure matches id")
						case p: Procedure =>
							sql"""delete from procedures where id=$id""".update.run.transact(xa)
							Right(id)
					}
			})
		}

		def insert(p: Procedure): Task[Failure | Entity.Id[Procedure]] = {
			upsertProcedure(p).attempt.map(f => f match {
				case l: Left[Throwable, Id[Procedure]]  => Left(l.value.toString)
				case r: Right[Throwable, Id[Procedure]] => Right(r.value)
			})
		}

		def update(id: Id[Procedure], f: Procedure => Procedure): Task[Failure | Id[Procedure]] = {
			find(id).attempt.map(m => m match {
				case l: Left[Throwable, Option[Procedure]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Procedure]] =>
					r.value.getOrElse(None) match {
						case None         => Left("No procedure matches id")
						case p: Procedure =>
							upsertProcedure(f(p)).runAsync
							Right(id)
					}
			})
		}

		def execute(pid: Id[Entity.Procedure]): Task[Failure | String] =
			delete(pid).map(f => f match {
				case r: Right[Failure, Id[Procedure]] => Right("Procedure executed")
				case l: Left[Failure, Id[Procedure]]  => Left(l.value)
			})

	}

	implicit def infoMeta: Meta[Information] = Meta[Double].xmap(Information.apply(_).get, _.toBytes)
	implicit def optionMeta: Meta[Option[String]] = Meta[String].xmap(Option.apply(_), _.get)
	implicit def verdictMeta: Meta[Verdict] = Meta[String].xmap(Verdict.fromString(_), _.toString)
	object Verdict {
		def fromString(s: String): Telemetry.Verdict = {
			s match {
				case "Ok"       => Telemetry.Ok
				case "Warning"  => Telemetry.Warning
				case "Critical" => Telemetry.Critical
			}
		}
	}

	def upsertNode(node: Node): Task[Id[Node]] = {
		// Note: ID can be none
		val Node(idOp, target, remark, status, logs) = node
		val target_host = target.host
		val target_port = target.port
		var targetUsername = target.username
		var targetKeyPath = ""
		var targetPassword = "" // Real bad. REALLY BAD
		target match {
			case t: SshKeyTarget      =>
				targetKeyPath = t.keyPath
			case t: SshPasswordTarget =>
				targetPassword = t.password
		}

		val stat = status.getOrElse(None) match {
			case onl: Online => onl
			case err: Error  => err
			case None        => None
			case _           => status.toString
		}


		logs.map(l => {
			val p = l._1
			val b = l._2
			sql"""
				 | MERGE INTO LOGS KEY(id) VALUES($idOp, $p, $b)
         """.update.run
		})

		stat match {
			case None => None

			case s: String => sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, $s, NULL, NULL, NULL)""".update.run

			case err: Error =>
				val err_string = err.error;

				sql""" | MERGE INTO STATUS KEY(id) VALUES ($idOp, 'Error', $err_string, NULL, NULL);""".update.run

			case onl: Online => {
				val Online(verdict, reason, telemetry) = onl
				val Telemetry(arch, uptime, users,
				processorCount, loadAverage,
				cpuStat, memoryStat, threadStat, netStats, diskStats) = telemetry
				// Note netStats and diskStats are Map[String, T]
				val ThreadStat(running, sleeping, stopped, zombie) = threadStat
				sql"""|MERGE INTO THREADSTAT KEY(id) VALUES ($idOp, $running, $sleeping, $stopped, $zombie);""".update.run

				val MemoryStat(total, free, used, cached) = memoryStat
				sql"""|MERGE INTO MEMORYSTAT KEY(id) VALUES ($idOp, $total, $free, $used, $cached);""".update.run

				netStats.map(m => {
					val iface = m._1
					val NetStat(mac: String,
					inet: String,
					bcast: String,
					mask: String,
					inet6: Option[String],
					scope: String, tx: Information, rx: Information) = m._2
					sql""" | MERGE INTO NETSTAT KEY(id) VALUES ( $idOp, $iface, $mac, $inet, $bcast, $mask, $inet6, $scope, $tx, $rx);""".update.run
				})

				diskStats.map(m => {
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

		private def findThreadStat(id: Id[Node]): Task[List[ThreadStat]] = {
			sql"""select running, sleeping, stopped, zombie from threadStat where id=$id"""
				.query[ThreadStat]
				.map(f => f).to[List].transact(xa)
		}

		private def findMemStat(id: Id[Node]): Task[List[MemoryStat]] = {
			sql"""select total, free, used, cache from memoryStat where id=$id"""
				.query[MemoryStat]
				.map(f => f).to[List].transact(xa)
		}

		private def findNetStat(id: Id[Node]): Task[List[(Iface, NetStat)]] = {
			sql"""select iface, mac, inet, bcast, mask, inet6, scope, tx1, rx1 from netStat where id=$id"""
				.query[(Iface, NetStat)]
				.map(f => (f._1, f._2))
				.to[List].transact(xa)
		}

		private def findDiskStat(id: Id[Node]): Task[List[(Path, DiskStat)]] = {
			sql"""select path, freeVal, freeUnits from diskStat where id=$id"""
				.query[(Path, DiskStat)]
				.map(f => (f._1, f._2)).to[List].transact(xa)
		}

		private def findTelemetry(id: Id[Node]): Task[List[Telemetry]] = {

			var memStat: MemoryStat = MemoryStat(
				Information.apply(-1).get,
				Information.apply(-1).get,
				Information.apply(-1).get,
				Information.apply(-1).get)
			findMemStat(id)
				.runAsync
				.foreach(f => {
					memStat = f.head
				})
			var threadStat: ThreadStat = ThreadStat(-1, -1, -1, -1)
			findThreadStat(id)
				.runAsync
				.foreach(f => {
					threadStat = f.head
				})
			var netStat = Map[Iface, NetStat]();
			findNetStat(id)
				.runAsync
				.foreach(f => {
					netStat.updated(f.head._1, f.head._2)
				})
			var diskStat = Map[Path, DiskStat]();
			findDiskStat(id)
				.runAsync
				.foreach(f => {
					diskStat.updated(f.head._1, f.head._2)
				})


			sql"""select arch, duration, users, processorCount, loadAverage from telemetry where id=$id"""
				.query[(String, Duration, Long, Int, Double)]
				.map(f => {
					Telemetry(f._1, f._2, f._3, f._4, f._5, memStat, threadStat, netStat, diskStat)
				})
				.to[List].transact(xa)
		}

		private def findStatus(id: Id[Node]): Task[List[Option[Telemetry.Status]]] = {
			sql"""select statMessage, error, verdict, reason from status where id=$id"""
				.query[Telemetry.StatusGen]
				.map(f => Option({
					f.statMessage match {
						case "Offline" => Offline
						case "Timeout" => Timeout
						case "Error"   => Error(f.error)
						case "Online"  =>
							val verdict = f.verdict match {
								case "Ok"       => Ok
								case "Warning"  => Warning
								case "Critical" => Critical
							}
							var telemetry: Telemetry = Telemetry(
								"",
								Duration.ZERO,
								-1,
								-1,
								-1,
								CpuStat(0, 0),
								MemoryStat(Information.apply(-1).get, Information.apply(-1).get, Information.apply(-1).get, Information.apply(-1).get),
								ThreadStat(-1, -1, -1, -1),
								Map[Iface, NetStat](),
								Map[Path, DiskStat]()
							)
							findTelemetry(id).runAsync.foreach(f => {telemetry = f.head})
							Online(verdict, Option(f.reason), telemetry)
					}
				}))
				.to[List].transact(xa)
		}

		private def findTarget(id: Id[Node]): Task[List[Entity.Target]] = {
			sql"""select targetHost, targetPort, targetUsername, targetKeyPath, targetPassword from nodes where id=$id"""
				.query[Entity.TargetGen]
				.map(f => {
					f.password match {
						case "" => SshKeyTarget(f.host, f.port, f.username, f.keyPath)
						case _  => SshPasswordTarget(f.host, f.port, f.username, f.password)
					}
				})
				.to[List].transact(xa)
		}

		private def findLogs(id: Id[Node]): Task[List[(Path, ByteSize)]] = {
			sql""" select path, byteSize from logs where id=$id"""
				.query[(Path, ByteSize)]
				.map(f => (f._1, f._2))
				.to[List].transact(xa)
		}

		def list(): Task[Seq[Id[Node]]] = {
			sql"""
				 |select id from nodes"""
				.query[Id[Node]].to[Seq].transact(xa)
		}

		def find(id: Id[Node]): Task[Option[Node]] = {

			var target: Entity.Target = SshKeyTarget("", -1, "", "")
			findTarget(id).runAsync.foreach(f => {target = f.head})
			var status: Option[Telemetry.Status] = Option.empty
			findStatus(id).runAsync.foreach(f => {status = f.head})
			var logs: Map[Path, ByteSize] = Map[Path, ByteSize]()
			findLogs(id).runAsync.foreach(f => logs.updated(f.head._1, f.head._2))

			sql"""
				 | select id, remark from node where id=$id
        """
				.query[(Id[Node], String)]
				.map(f => {
					Entity.Node(f._1, target, f._2, status, logs)
				})
				.to[List]
				.map(f => {
					f.headOption
				})
				.transact(xa)
		}

		def insert(n: Node): Task[Failure | Id[Node]] = {
			upsertNode(n).attempt.map(f => f match {
				case l: Left[Throwable, Id[Node]]  => Left(l.value.toString)
				case r: Right[Throwable, Id[Node]] => Right(r.value)
			})
		}

		def update(id: Id[Node], f: Node => Node): Task[Failure | Id[Node]] = {
			find(id).attempt.map(m => m match {
				case l: Left[Throwable, Option[Node]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Node]] =>
					r.value.getOrElse(None) match {
						case None    => Left("No node matches id")
						case n: Node =>
							upsertNode(f(n)).runAsync
							Right(id)
					}
			})
		}


		def delete(id: Id[Node]): Task[Failure | Id[Node]] = {
			find(id).attempt.map(f => f match {
				case l: Left[Throwable, Option[Node]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Node]] =>
					r.value.getOrElse(None) match {
						case None    => Left("No node matches id")
						case n: Node =>
							sql"""delete from nodes where id=$id""".update.run.transact(xa)
							Right(id)
					}
			})
		}

		def telemetries(nid: Id[Entity.Node])(bound: Bound): Task[Option[TelemetrySeries]] = {
			var series = TreeMap.empty
			find(nid).map({ f =>
				val status = findStatus(nid).runSyncUnsafe(scala.concurrent.duration.Duration(1, scala.concurrent.duration.HOURS)).head.get
				Option(TelemetrySeries(f.get.id, series.insert(ZonedDateTime.now(), status)))
			})
		}

		def logs(nid: Id[Entity.Node])(path: Path)(bound: Bound): Task[Option[LogSeries]] = {
			var series = TreeMap.empty
			findLogs(nid).map(f => {
				var seq = Seq("")
				f.foreach(f => {seq = seq :+ f.toString()})
				Option(LogSeries(nid, series.insert(ZonedDateTime.now(), seq)))
			})
		}
		def meta(nid: Id[Entity.Node]): Task[Option[Node]] = find(nid)

		def ids: Observable[Set[Id[Entity.Node]]] = {
			Observable.fromTask(list().map(f => f.toSet))
		}

		def telemetryDelta: Observable[Id[Entity.Node]] = ids.map(f => f.head) // ??
		def logDelta: Observable[Id[Entity.Node]] = ids.map(f => f.head)

		def find(target: Target): Task[Option[Node]] = {
			val username = target.username
			var ret: Task[Option[Node]] = Task.apply(Option.empty)
			val id = sql"""select id from nodes where targetUsername=$username"""
				.query[Id[Node]].unique.transact(xa).runAsync.foreach(f => ret = find(f))

			return ret
		}

		def execute(nid: Id[Entity.Node])(pid: Id[Entity.Procedure]): Task[Failure | String] =
			delete(nid).map(f => f match {
				case r: Right[Failure, Id[Node]] => Right("Node executed")
				case l: Left[Failure, Id[Node]]  => Left(l.value)
			})

		def persist(nid: Id[Entity.Node],
					time: ZonedDateTime,
					status: Status): Task[Failure | Id[Entity.Node]] = {

			find(nid).attempt.map(m => m match {
				case l: Left[Throwable, Option[Node]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Node]] =>
					r.value.getOrElse(None) match {
						case None    => Left("Node persist failure, no node found" + time.toString)
						case n: Node =>
							var Node(id, target, remark, _, logs) = n
							val pn = Node(id, target, remark, Option(status), logs)
							upsertNode(pn).runAsync
							Right(nid)
					}
			})
		}

		def persist(nid: Id[Entity.Node],
					time: ZonedDateTime,
					path: Path,
					lines: Seq[Line]): Task[Failure | Id[Entity.Node]] = {
			find(nid).attempt.map(m => m match {
				case l: Left[Throwable, Option[Node]]  => Left(l.value.toString)
				case r: Right[Throwable, Option[Node]] =>
					r.value.getOrElse(None) match {
						case None    => Left("Node persist failure, no node found" + time.toString)
						case n: Node =>
							var l = n.logs
							l.updated(path, lines.toString)
							var Node(id, target, remark, status, _) = n
							val pn = Node(id, target, remark, status, l)
							upsertNode(pn).runAsync
							Right(nid)
					}
			})
		}

	}
}
