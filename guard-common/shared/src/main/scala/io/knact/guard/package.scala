package io.knact

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME

import cats.{Contravariant, FgtlatMap, Monad}
import enumeratum.EnumEntry
import io.circe._
import io.knact.guard.Entity._
import io.knact.guard.Telemetry.Status
import monix.eval.Task
import monix.reactive.Observable
import shapeless.tag
import shapeless.tag.@@
import squants.information.{Bytes, Information}

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


	implicit def informationEncoderProof: Encoder[Information] =
		Encoder.encodeLong.contramap[Information](_.toBytes.toLong)
	implicit def informationDecoderProof: Decoder[Information] =
		Decoder.decodeLong.map {Bytes(_)}

	// XXX we can go from Comparable<A> to Ordering[A],
	// but ZonedDateTime is ZonedDataTime <: ChronoZonedDateTime<?>, bit sloppy on Java's impl.
	// so we derive a Ordering[ZonedDateTime] by hand
	implicit val zdtOrd: Ordering[ZonedDateTime] = _ compareTo _

	type Line = String
	type Path = String
	type ByteSize = Long
	type Percentage = Double

	// bounds and parameters

	sealed trait Sort extends EnumEntry
	object Sort extends enumeratum.Enum[Sort] {
		case object Asc extends Sort
		case object Desc extends Sort
		val values = findValues
	}

	case class Parameters(limit: Int, offset: Int, sort: Sort)
	case class Bound(start: Option[ZonedDateTime] = None, end: Option[ZonedDateTime] = None)

	// fake coproducts :)
	type Failure = String
	type |[A, B] = Either[A, B]

	trait Repository[A <: Entity[A], F[_]] {
		def list(): Task[Seq[Id[A]]] // TODO Seq or Set?
		def find(id: Id[A]): F[Option[A]]
		def delete(id: Id[A]): F[Failure | Id[A]]
		def insert(a: A): F[Failure | Id[A]]
		def update(id: Id[A], f: A => A): F[Failure | Id[A]]
	}
	trait ProcedureRepository extends Repository[Procedure, Task] {}
	trait NodeRepository extends Repository[Entity.Node, Task] {

		def ids: Observable[Set[Id[Entity.Node]]]
		def entities: Observable[Set[Node]] = ids
			.switchMap { ns => Observable.fromTask(Task.traverse(ns)(find)) }
			.map {_.flatten.toSet}
		def telemetryDelta: Observable[Id[Entity.Node]]
		def logDelta: Observable[Id[Entity.Node]]

		def find(target: Target) : Task[Option[Node]]
		def meta(nid: Id[Entity.Node]): Task[Option[Node]]
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


	final val ProcedurePath = "procedure"
	final val NodePath      = "node"

	sealed trait RemoteResult[+A] {
		def toEither: Either[String, A]
	}
	case class ConnectionError(e: Throwable) extends RemoteResult[Nothing] {
		override def toEither: Either[String, Nothing] = Left(e.getMessage)
	}
	case class ServerError(reason: String) extends RemoteResult[Nothing] {
		override def toEither: Either[String, Nothing] = Left(reason)
	}
	case class ClientError(reason: String) extends RemoteResult[Nothing] {
		override def toEither: Either[String, Nothing] = Left(reason)
	}
	case class DecodeError(reason: String) extends RemoteResult[Nothing] {
		override def toEither: Either[String, Nothing] = Left(reason)
	}
	case object NotFound extends RemoteResult[Nothing] {
		override def toEither: Either[String, Nothing] = Left("Not found")
	}
	case class Found[+A](a: A) extends RemoteResult[A] {
		override def toEither: Either[String, A] = Right(a)
	}

	implicit val remoteResultInstance: Monad[RemoteResult] = new Monad[RemoteResult] {
		override def pure[A](x: A): RemoteResult[A] = Found(x)
		override def flatMap[A, B](fa: RemoteResult[A])(f: A => RemoteResult[B]): RemoteResult[B] = fa match {
			case Found(a) => f(a)
			// TODO this is trivially true, but can we do better?
			case v => v.asInstanceOf[RemoteResult[B]]
		}
		@tailrec
		override def tailRecM[A, B](a: A)(f: A => RemoteResult[Either[A, B]]): RemoteResult[B] = f(a) match {
			// TODO this is trivially true, but can we do better?
			case v               => v.asInstanceOf[RemoteResult[B]]
			case Found(Left(x))  => tailRecM(x)(f)
			case Found(Right(x)) => Found(x)
		}
	}

	type ResultF[X] = Task[RemoteResult[X]]

	trait EntityService[A <: Entity[A]] {
		def list(): ResultF[Seq[Id[A]]]
		def find(id: Id[A]): ResultF[A]
		def insert(a: A): ResultF[Outcome[A]]
		def update(id: Id[A], a: A): ResultF[Outcome[A]]
		def delete(id: Id[A]): ResultF[Outcome[A]]
	}

	// XXX to make sure we have proof for all the the JSON mappings, we find them here

	@inline private[guard] final def ensureCodec[T]
	(implicit enc: Encoder[T], dec: Decoder[T]) = (enc, dec)


}
