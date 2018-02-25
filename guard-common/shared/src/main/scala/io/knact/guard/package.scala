package io.knact

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME

import cats.{Contravariant, FlatMap, Monad}
import enumeratum.EnumEntry
import io.circe._
import io.knact.guard.Entity._
import io.knact.guard.Telemetry.Status
import monix.eval.Task
import monix.reactive.Observable
import shapeless.tag
import shapeless.tag.@@

import scala.annotation.tailrec
import scala.util.Try

package object guard {


	// XXX we are using tagged refinements, which is not supported
	// wanted : taggedEncoderProof[T, U](implicit ev : Encoder[T]) : Encoder[T @@ U]=???
	// that causes ev to diverge, if the solver had SAT or something maybe it would work...
	// see https://github.com/circe/circe/issues/220
	implicit def taggedLongEncoderProof[U]: Encoder[Long @@ U] = {
		import cats.syntax.contravariant._
		Encoder[Long].narrow
	}
	implicit def taggedLongDecoderProof[U]: Decoder[Long @@ U] = {
		Decoder[Long].map { l => tag[U][Long](l) }
	}

	implicit def zdtKeyEncoder: KeyEncoder[ZonedDateTime] =
		key => key.format(ISO_ZONED_DATE_TIME)

	implicit def zdtKeyDecoder: KeyDecoder[ZonedDateTime] =
		key => Try {ZonedDateTime.parse(key)}.toOption


	type Line = String
	type Path = String
	type ByteSize = Long

	// bounds and parameters

	sealed trait Sort extends EnumEntry
	object Sort extends enumeratum.Enum[Sort] {
		case object Asc extends Sort
		case object Desc extends Sort
		val values = findValues
	}

	case class Parameters(limit: Int, offset: Int, sort: Sort)
	case class Bound(start: Option[ZonedDateTime], end: Option[ZonedDateTime])

	// fake coproducts :)
	type Failure = String
	type |[A, B] = Either[A, B]

	trait Repository[A <: Entity[A], F[_]] {
		def list(): Task[Seq[Id[A]]]
		def find(id: Id[A]): F[Option[A]]
		def delete(id: Id[A]): F[Failure | Id[A]]
		def insert(a: A): F[Failure | Id[A]]
		def update(id: Id[A], f: A => A): F[Failure | Id[A]]
	}
	trait GroupRepository extends Repository[Group, Task] {}
	trait ProcedureRepository extends Repository[Procedure, Task] {}
	trait NodeRepository extends Repository[Entity.Node, Task] {
		def observable : Observable[Vector[Id[Entity.Node]]]
		def telemetries(nid: Id[Entity.Node])(bound: Bound): Task[Option[TelemetrySeries]]
		def logs(nid: Id[Entity.Node])(path: Path)(bound: Bound): Task[Option[LogSeries]]
		def execute(nid: Id[Entity.Node])(pid: Id[Procedure]): Task[Failure | String]

		def persist(nid: Id[Entity.Node],
					time: ZonedDateTime,
					status: Status): Task[Failure | Id[Entity.Node]]
		def persist(nid: Id[Entity.Node],
					time: ZonedDateTime,
					path: Path,
					lines: Seq[Line]): Task[Failure | Id[Entity.Node]]
	}


	final val GroupPath     = "group"
	final val ProcedurePath = "procedure"
	final val NodePath      = "node"

	sealed trait RemoteResult[+A]
	case class ConnectionError(e: Throwable) extends RemoteResult[Nothing]
	case class ServerError(reason: String) extends RemoteResult[Nothing]
	case class ClientError(reason: String) extends RemoteResult[Nothing]
	case class DecodeError(reason: String) extends RemoteResult[Nothing]
	case object NotFound extends RemoteResult[Nothing]
	case class Found[+A](a: A) extends RemoteResult[A]

	implicit val remoteResultInstance: Monad[RemoteResult] = new Monad[RemoteResult] {
		override def pure[A](x: A): RemoteResult[A] = Found(x)
		override def flatMap[A, B](fa: RemoteResult[A])(f: A => RemoteResult[B]): RemoteResult[B] = fa match {
			case Found(a)   => f(a)
				// TODO this is trivially true, but can we do better?
			case v => v.asInstanceOf[RemoteResult[B]]
		}
		@tailrec
		override def tailRecM[A, B](a: A)(f: A => RemoteResult[Either[A, B]]): RemoteResult[B] = f(a) match {
			// TODO this is trivially true, but can we do better?
			case v => v.asInstanceOf[RemoteResult[B]]
			case Found(Left(x))  => tailRecM(x)(f)
			case Found(Right(x)) => Found(x)
		}
	}


	trait EntityService[A <: Entity[A]] {
		def list(): Task[RemoteResult[Seq[Id[A]]]]
		def find(id: Long): Task[RemoteResult[A]]
		def insert(a: A): Task[RemoteResult[Outcome[A]]]
		def update(id: Long, a: A): Task[RemoteResult[Outcome[A]]]
		def delete(id: Long): Task[RemoteResult[Outcome[A]]]
	}


	// XXX to make sure we have proof for all the the JSON mappings, we find them here

	@inline private[guard] final def ensureCodec[T]
	(implicit enc: Encoder[T], dec: Decoder[T]) = (enc, dec)


}
