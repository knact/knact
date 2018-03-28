package io.knact.guard.server

import java.time.{Instant, LocalDateTime, ZoneOffset, ZonedDateTime}

import cats.data.EitherT
import cats.implicits._
import com.google.common.base.Ascii
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Json, _}
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.syntax._
import io.knact.guard
import io.knact.guard.Entity.{Altered, Event, Failed, Id, Node, NodeUpdated, PoolChanged, Procedure, ServerStatus, id => coerce}
import io.knact.guard.{NodePath, _}
import io.knact.guard.server.component.MxMetric
import monix.eval.Task
import monix.reactive.Observable
import org.http4s.{HttpService, _}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.AutoSlash


class ApiService(context: ApiContext, config: Config) extends Http4sDsl[Task] with LazyLogging {

	val (nodes, procedures) = context.repos


	private def boxAlteration[A](x: Either[String, Id[A]]): Task[Response[Task]] = x match {
		case Right(id)   => Ok(Altered(id).asJson)
		case Left(error) => BadRequest(Failed(error).asJson)
	}

	type E[A] = guard.Entity[A]

	private def list[A <: E[A]](repo: Repository[A, Task]) =
		repo.list() >>= { xs => Ok(xs.asJson) }
	private def find[A <: E[A]](repo: Repository[A, Task], id: Int)(implicit ev: Encoder[A]) =
		repo.find(coerce(id)).flatMap {
			case None    => NotFound()
			case Some(x) => Ok(x.asJson)
		}
	private def insert[A <: E[A]](repo: Repository[A, Task], req: Request[Task])
								 (implicit ev: EntityDecoder[Task, A]) = (for {
		decoded <- req.attemptAs[A].leftMap {_.message}
		id <- EitherT(repo.insert(decoded))
	} yield id).value >>= boxAlteration
	private def update[A <: E[A]](repo: Repository[A, Task], req: Request[Task], id: Int)
								 (implicit ev: EntityDecoder[Task, A => A]) = (for {
		f <- req.attemptAs[A => A].leftMap {_.message}
		updated <- EitherT(repo.update(coerce(id), f))
	} yield updated).value >>= boxAlteration
	private def delete[A <: E[A]](repo: Repository[A, Task], id: Int) =
		repo.delete(coerce(id)) >>= boxAlteration


	// GET 	   / 		      :: Stat

	// GET     group/node/        :: Seq[Id]full
	// GET     group/node/{id}    :: Node
	// POST    group/node/        :: Node => Id
	// POST    group/node/{id}    :: Node => Id
	// DELETE  group/node/{id}    :: Id
	// GET     group/node/{id}/telemetry/&+{bound}   :: TelemetrySeries
	// GET     group/node/{id}/log/{file}/&+{bound}  :: LogSeries

	// GET     procedure/           :: Seq[Id]
	// GET     procedure/{id}       :: Procedure
	// GET     procedure/{id}/code  :: Procedure
	// POST    procedure/           :: Procedure  => Id
	// POST    procedure/{id}       :: Procedure  => Id
	// POST    procedure/{id}/code  :: String     => Id
	// DELETE  procedure/{id}       :: EntityId
	// POST    procedure/{id}/exec

	import io.knact.guard.{NodePath, ProcedurePath}

	implicit def entityDecoderForAllA[A](implicit ev: Decoder[A]): EntityDecoder[Task, A] =
		jsonOf[Task, A]

	/*_*/
	private val statService = HttpService[Task] {
		case GET -> Root => Ok(
			for {
				ns <- nodes.list()
				ps <- procedures.list()
			} yield ServerStatus(
				version = context.version,
				nodes = ns.size,
				procedures = ps.size,
				load = MxMetric.processCpuLoad().getOrElse(0.0),
				memory = MxMetric.processHeapMemory(),
				startTime = context.startTime).asJson
		)
	}
	/*_*/

	/*_*/
	private val procedureService = HttpService[Task] {
		case GET -> Root / ProcedurePath                   => list(procedures)
		case GET -> Root / ProcedurePath / IntVar(id)      => find(procedures, id)
		case req@POST -> Root / ProcedurePath              => insert(procedures, req)
		case req@POST -> Root / ProcedurePath / IntVar(id) => update(procedures, req, id)
		case DELETE -> Root / ProcedurePath / IntVar(id)   => delete(procedures, id)

		// TODO telemetry and log endpoints
	}
	/*_*/

	implicit val zdtQueryParamDecoder: QueryParamDecoder[ZonedDateTime] = QueryParamDecoder[Long]
		.map { epoch => ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC) }

	object StartVar extends OptionalQueryParamDecoderMatcher[ZonedDateTime]("start")
	object EndVar extends OptionalQueryParamDecoderMatcher[ZonedDateTime]("end")

	/*_*/
	private val nodeService = HttpService[Task] {
		case GET -> Root / NodePath                       => list(nodes)
		case GET -> Root / NodePath / IntVar(id)          => find(nodes, id)
		case GET -> Root / NodePath / IntVar(id) / "meta" =>
			nodes.meta(coerce(id)).flatMap {
				case None    => NotFound()
				case Some(x) => Ok(x.asJson)
			}
		case req@POST -> Root / NodePath                  => insert(nodes, req)
		case req@POST -> Root / NodePath / IntVar(id)     => update(nodes, req, id)
		case DELETE -> Root / NodePath / IntVar(id)       => delete(nodes, id)

		case GET -> Root / NodePath / IntVar(id) / "telemetry" :? StartVar(s) +& EndVar(e)  =>
			nodes.telemetries(coerce(id))(Bound(s, e)).flatMap {
				case None    => NotFound()
				case Some(x) => Ok(x.asJson)
			}
		case GET -> Root / NodePath / IntVar(id) / "log" / path :? StartVar(s) +& EndVar(e) =>
			nodes.logs(coerce(id))(path)(Bound(s, e)).flatMap {
				case None    => NotFound()
				case Some(x) => Ok(x.asJson)
			}
		case GET -> Root / NodePath / "events"                                              =>
			import fs2._
			import org.http4s.server.websocket._
			import org.http4s.websocket.WebsocketBits._

			def asFrame(event: Event): WebSocketFrame = Text(event.asJson.noSpaces)

			val fs = for {
				d <- fs2.Stream.eval(fs2.async.unboundedQueue[Task, WebSocketFrame])
				_ <- fs2.Stream.eval(Task.apply {
					nodes.ids
						.mapTask(ns => d.enqueue1(asFrame(PoolChanged(ns))))
						.subscribe()
					// TODO technically, an event about pool invalidates any buffered node delta
					Observable.merge(nodes.logDelta, nodes.telemetryDelta)
						.bufferTimed(config.eventInterval)
						.map {_.toSet}
						.filter {_.nonEmpty}
						.mapTask(ns => d.enqueue1(asFrame(NodeUpdated(ns))))
						.subscribe()
				})
				v <- d.dequeue
			} yield v
			WebSocketBuilder[Task].build(fs, Sink(v => Task {
				val summary = v match {
					case Text(msg, _) => Ascii.truncate(msg, 80, "...")
					case frame        => frame.getClass.toGenericString
				}
				logger.info(s"Client responded to listen only channel with $summary")
			}))
	}
	/*_*/


	/*_*/
	// trailing slashes make no difference
	lazy val services: HttpService[Task] = AutoSlash(statService <+>
													 procedureService <+>
													 nodeService)
	/*_*/

}
