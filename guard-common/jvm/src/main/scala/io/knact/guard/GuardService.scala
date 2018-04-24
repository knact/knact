package io.knact.guard

import java.net.URI
import java.time.ZonedDateTime
import java.util.Collections.{singletonList => JSingletonList}
import java.util.concurrent.{Future, TimeUnit}

import javax.websocket
import javax.websocket._
import cats.implicits._
import cats.kernel.Semigroup
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, _}
import io.knact.guard
import io.knact.guard.Entity.{Event, Id, LogSeries, Node, NodeUpdated, Outcome, PoolChanged, Procedure, ServerStatus, Target, TelemetrySeries, TimeSeries}
import io.knact.guard.GuardService.{NodeError, NodeHistory, NodeItem, NodeService, ProcedureService, send}
import io.knact.guard.Telemetry.Status
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, Subject}
import org.glassfish.tyrus.client.{ClientManager, ClientProperties}
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.{EntityDecoder, Method, Request, Status, Uri, _}

import scala.collection.immutable.TreeMap
import scala.util.Try


// TODO test me
class GuardService private(val baseUri: Uri,
						   private val close: () => Unit,
						   private val eventSubject: Subject[Event, Event])
	extends Http4sClientDsl[Task] {

	def terminate(): Unit = close()

	// bring all unrelated decoder instances into scope, this arises
	// for Node -> TelemetrySeries | LogSeries or in prodecure(potentially)
	implicit def entityDecoderForAllA[A](implicit ev: Decoder[A]): EntityDecoder[Task, A] =
		jsonOf[Task, A]


	val events: Observable[Event] = eventSubject.cache(1).share

	def status(): ResultF[ServerStatus] = send[ServerStatus](Method.GET(baseUri))

	def procedures(): ProcedureService = new Http4sEntityService[Procedure] with ProcedureService {
		override def path: Uri = baseUri / ProcedurePath
		// TODO imp
		override def execute(id: Id[Node])(procedureId: Id[Procedure]): ResultF[String] = ???
	}

	def nodes(): NodeService = new Http4sEntityService[Node] with NodeService {
		override def path: Uri = baseUri / NodePath
		override def meta(id: Id[Node]): ResultF[Node] =
			GuardService.send[Node](Method.GET(this.path / id.toString / "meta"))
		override def telemetry(id: Id[Node], bound: Bound): ResultF[TelemetrySeries] = {
			GuardService.send[TelemetrySeries](Method.GET((this.path / id.toString / "telemetry")
				.withOptionQueryParam("start", bound.start.map {_.toInstant.toEpochMilli})
				.withOptionQueryParam("end", bound.end.map {_.toInstant.toEpochMilli})))
		}
		override def log(id: Id[Node], path: Path, bound: Bound): ResultF[LogSeries] = {
			GuardService.send[LogSeries](Method.GET((this.path / id.toString / "log")
				.withOptionQueryParam("start", bound.start.map {_.toInstant.toEpochMilli})
				.withOptionQueryParam("end", bound.end.map {_.toInstant.toEpochMilli})))
		}


		override def observe(): Observable[Either[String, Map[Id[Node], NodeItem]]] = {

			// Id -> NodeItem
			type NodeItems = Map[Id[Node], NodeItem]
			type Outcome = Either[String, NodeItems]

			def pull(ids: Seq[Id[Node]]): Task[NodeItems] = {
				Task.wanderUnordered(ids) { id =>
					nodes().find(id).map {
						case ConnectionError(e)  => NodeError(id, s"Connection failed: ${e.getMessage}")
						case ServerError(reason) => NodeError(id, s"Server error: $reason")
						case ClientError(reason) => NodeError(id, s"Client error: $reason")
						case DecodeError(reason) => NodeError(id, s"Decode error: $reason")
						case NotFound            => NodeError(id, s"Node not found")
						case Found(node)         =>
							val now = ZonedDateTime.now()
							NodeHistory(id = node.id,
								target = node.target,
								remark = node.remark,
								status = node.status.fold(TreeMap.empty[ZonedDateTime, Telemetry.Status]) { x => TreeMap(now -> x) }
								, log = TreeMap(now -> node.logs))
					}.map { item => item.id -> item }
				}.map {_.toMap}
			}

			def retain[K, V](xs: Map[K, V], ks: Set[K]): Map[K, V] = xs.filterKeys(ks.contains)

			events.scanTask(for {
				ids <- nodes().list().map {_.toEither}
				v <- ids match {
					case Left(e)   => Task.pure(Left(e))
					case Right(is) => pull(is).map {Right(_)}
				}
			} yield v) {
				// TODO right side
				case (Left(_), PoolChanged(pool))    => pull(pool.toSeq).map {Right(_)}
				case (Left(_), NodeUpdated(delta))   => pull(delta.toSeq).map {Right(_)}
				case (Right(xs), PoolChanged(pool))  => pull(pool.toSeq).map { ys =>
					//					val left = xs.keySet.intersect(ys.keySet).map { k => k -> xs(k) }.toMap
					Right(retain(xs, ys.keySet) |+| ys)
				}
				case (Right(xs), NodeUpdated(delta)) => pull(delta.toSeq).map { ys => Right(xs |+| ys) }
			}.doOnError {_.printStackTrace()}
		}
		override def observeSingle(id: Id[Node]): Observable[RemoteResult[TelemetrySeries]] = {
			def pull(start: Option[ZonedDateTime]): Task[(ZonedDateTime, guard.RemoteResult[Entity.TelemetrySeries])] = {
				for {
					now <- Task.eval {ZonedDateTime.now()}
					result <- nodes().telemetry(id, Bound(start))
				} yield now -> result
			}
			(Observable.now(()) ++ events.filter {
				case NodeUpdated(ns) => ns.contains(id)
				case _               => false
			}.map { _ => () }).scanTask(pull(None)) { case ((last, prev), _) =>
				pull(Some(last)).map { case (t, next) => t -> (prev |+| next) }
			}.map {_._2}
		}
	}
	abstract class Http4sEntityService[A <: Entity[A]](implicit
													   encoder: Encoder[A],
													   decoder: Decoder[A])
		extends EntityService[A] {
		def path: Uri
		protected[this] implicit val entityDecoder: EntityDecoder[Task, A]    = jsonOf[Task, A]
		protected[this] implicit val jsonEncoder  : EntityEncoder[Task, Json] = implicitly
		override def list(): ResultF[Seq[Id[A]]] = send[Seq[Id[A]]](Method.GET(path))
		override def find(id: Id[A]): ResultF[A] = send[A](Method.GET(path / id.toString))
		override def insert(a: A): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.POST(path, encoder(a)))
		override def update(id: Id[A], a: A): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.POST(path / id.toString, encoder(a)))
		override def delete(id: Id[A]): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.DELETE(path / id.toString))
	}

	override def toString = s"GuardService($baseUri)"
}

object GuardService {


	implicit val nodeHistorySemigroup: Semigroup[NodeItem] =
		Semigroup.instance[NodeItem] {
			case (l: NodeHistory, r: NodeHistory) =>
				r.copy(status = l.status ++ r.status, log = l.log ++ r.log)
			case (_: NodeError, r: NodeHistory)   => r
			case (_, e: NodeError)                => e
		}

	sealed trait NodeItem {
		def id: Id[Node]
	}

	case class StatusEntry(time: ZonedDateTime, event: String, reason: String)

	case class NodeError(id: Id[Node], reason: String) extends NodeItem

	case class NodeHistory(id: Id[Node],
						   target: Target,
						   remark: String,
						   status: TimeSeries[Telemetry.Status],
						   log: TimeSeries[Map[Path, ByteSize]]) extends NodeItem


	trait NodeService extends EntityService[Node] {
		def meta(id: Id[Node]): ResultF[Node]
		def telemetry(id: Id[Node], bound: Bound): ResultF[TelemetrySeries]
		def log(id: Id[Node], path: Path, bound: Bound): ResultF[LogSeries]
		def observe(): Observable[Either[String, Map[Id[Node], NodeItem]]]
		def observeSingle(id: Id[Node]): Observable[RemoteResult[TelemetrySeries]]
	}

	trait ProcedureService extends EntityService[Procedure] {
		def execute(id: Id[Node])(procedureId: Id[Procedure]): ResultF[String]
	}

	def apply(baseUri: String): Either[Throwable, GuardService] =
		for {
			uri <- Uri.fromString(baseUri)
			_ <- uri.scheme.toRight(new IllegalArgumentException("Missing scheme, try prepending `http://`"))
			authority <- uri.authority.toRight(new IllegalArgumentException("Missing authority, try adding a path after the scheme"))
			gs <- Try {
				val eventSubject: ConcurrentSubject[Event, Event] = ConcurrentSubject.publish[Event]
				val client: ClientManager = ClientManager.createClient(classOf[JdkClientContainer].getName)
				client.getProperties.put(ClientProperties.HANDSHAKE_TIMEOUT, 1000 * 10: Integer) // timeout
				val f: Future[Session] = client.asyncConnectToServer(new Endpoint {
						override def onOpen(s: Session, c: EndpointConfig): Unit = {
							s.addMessageHandler(new MessageHandler.Whole[Event] {
								override def onMessage(message: Event): Unit = eventSubject.onNext(message)
							})
						}
						override def onError(s: Session, e: Throwable): Unit = eventSubject.onError(e)
						override def onClose(s: Session, reason: CloseReason): Unit = eventSubject.onComplete()
					}, ClientEndpointConfig.Builder
						.create()
						.decoders(JSingletonList(classOf[Jsr356EventDecoder]))
						.encoders(JSingletonList(classOf[Jsr356EventEncoder]))
						.build(), URI.create(s"ws://$authority${uri.path}/$NodePath/events"))
				val session = f.get(10, TimeUnit.SECONDS)
				new GuardService(uri, { () => session.close() }, eventSubject)
			}.toEither
		} yield gs


	private class Jsr356EventEncoder extends websocket.Encoder.Text[Event] {
		override def init(config: EndpointConfig): Unit = ()
		override def encode(event: Event): String = event.asJson.spaces4
		override def destroy(): Unit = ()
	}

	private class Jsr356EventDecoder extends websocket.Decoder.Text[Event] {
		override def init(config: EndpointConfig): Unit = ()
		@throws[DecodeException]
		override def decode(s: String): Event = io.circe.parser.decode[Event](s).fold(
			e => throw new DecodeException(s, "Circe failed to decode", e),
			identity
		)
		override def willDecode(s: String): Boolean = true
		override def destroy(): Unit = ()
	}

	private def send[A](req: Task[Request[Task]])
					   (implicit d: EntityDecoder[Task, A]): Task[RemoteResult[A]] = {
		val resolved = req.map { r =>
			if (d.consumes.nonEmpty) {
				val m = d.consumes.toList
				r.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
			} else r
		}
		Http1Client[Task]() >>= {
			_.fetch(resolved) {
				case r@(Status.Successful(_)
						| Status.Informational(_)) =>
					r.attemptAs[A]
						.leftMap(_.message)
						.fold(DecodeError, Found(_))
				case Status.ClientError(r)         => r.status match {
					case Status.NotFound => Task.pure(NotFound)
					case _               => Task.pure(ClientError(r.status.reason))
				}
				case Status.ServerError(r)         => Task.pure(ServerError(r.status.reason))
				case Status.Redirection(_)         => ??? // TODO what can we do here?
			}.onErrorHandle(ConnectionError)
		}
	}

}
