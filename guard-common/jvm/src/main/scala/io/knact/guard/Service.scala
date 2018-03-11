package io.knact.guard

import cats._
import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.{Decoder, Encoder, Json, _}
import io.knact.guard.Entity.{Id, LogSeries, Node, Outcome, Procedure, ServerStatus, TelemetrySeries}
import io.knact.guard.Service.send
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.{EntityDecoder, Method, Request, Response, Status, Uri}
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.Http1Client
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s._
import org.http4s.client.dsl.Http4sClientDsl


// TODO test me
class Service(val baseUri: Uri)  extends Http4sClientDsl[Task] {


	// bring all unrelated decoder instances into scope, this arises
	// for Node -> TelemetrySeries | LogSeries or in prodecure(potentially)
	implicit def entityDecoderForAllA[A](implicit ev: Decoder[A]): EntityDecoder[Task, A] =
		jsonOf[Task, A]

	def status(): ResultF[ServerStatus] = send[ServerStatus](Method.GET(baseUri))

	def procedures(): EntityService[Procedure] = new Http4sEntityService[Procedure] {
		override def path: Uri = baseUri / ProcedurePath
		override protected[this] implicit val decoder: Decoder[Procedure] = implicitly
		override protected[this] implicit val encoder: Encoder[Procedure] = implicitly
		// TODO the rest of the endpoints
	}

	def nodes(): EntityService[Node] {
		def telemetry(id: String, bound: Bound): ResultF[TelemetrySeries]
		def log(id: String, path: Path, bound: Bound): ResultF[LogSeries]
	} = new Http4sEntityService[Node] {
		override def path: Uri = baseUri / NodePath
		override protected[this] implicit val decoder: Decoder[Node] = implicitly
		override protected[this] implicit val encoder: Encoder[Node] = implicitly

		def telemetry(id: String, bound: Bound): ResultF[TelemetrySeries] =
			Service.send[TelemetrySeries](Method.GET(path))
		def log(id: String, path: Path, bound: Bound): ResultF[LogSeries] =
			Service.send[LogSeries](Method.GET(???))

	}


	type ResultF[X] = Task[RemoteResult[X]]
	private trait Http4sEntityService[A <: Entity[A]] extends EntityService[A] {
		def path: Uri
		protected[this] implicit val decoder: Decoder[A]
		protected[this] implicit val encoder: Encoder[A]
		protected[this] implicit val entityDecoder: EntityDecoder[Task, A]    = jsonOf[Task, A]
		protected[this] implicit val jsonEncoder  : EntityEncoder[Task, Json] = implicitly
		override def list(): ResultF[Seq[Id[A]]] = send[Seq[Id[A]]](Method.GET(path))
		override def find(id: Long): ResultF[A] = send[A](Method.GET(path / id.toString))
		override def insert(a: A): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.POST(path, encoder(a)))
		override def update(id: Long, a: A): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.POST(path / id.toString, encoder(a)))
		override def delete(id: Long): ResultF[Outcome[A]] =
			send[Outcome[A]](Method.DELETE(path / id.toString))
	}


}

object Service {

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

	//	implicit def entityDecoderProof[A](implicit ev: Decoder[A]): EntityDecoder[Task, A] =
	//		jsonOf[Task, A]


}
