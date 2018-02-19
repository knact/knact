package io.knact.guard.server

import cats.data.EitherT
import cats.implicits._
import io.circe.{Json, _}
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.syntax._
import io.knact.guard
import io.knact.guard.Entity.{Altered, Failed, Group, Id, Node, Procedure, id => coerce}
import io.knact.guard._
import monix.eval.Task
import org.http4s.{HttpService, _}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl


class ApiService(dependency: ApiContext) extends Http4sDsl[Task] {

	val (groups, nodes, procedures) = dependency.repos

	private implicit val groupDecoder          = jsonOf[Task, Group]
	private implicit val groupPatchDecoder     = jsonOf[Task, Group => Group]
	private implicit val nodeDecoder           = jsonOf[Task, Node]
	private implicit val nodePatchDecoder      = jsonOf[Task, Node => Node]
	private implicit val procedureDecoder      = jsonOf[Task, Procedure]
	private implicit val procedurePatchDecoder = jsonOf[Task, Procedure => Procedure]

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


	private final val Group     = "group"
	private final val Node      = "node"
	private final val Procedure = "procedure"

	// GET     group          :: Seq[Id]
	// GET     group/{id}     :: Group
	// POST    group	      :: Group => Id
	// POST    group/{id}	  :: Group => Id
	// DELETE  group/{id}     :: Id

	// GET     group/node/        :: Seq[Id]
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

	private val groupService = HttpService[Task] {
		case GET -> Root / Group                   => list(groups)
		case GET -> Root / Group / IntVar(id)      => find(groups, id)
		case req@POST -> Root / Group              => insert(groups, req)
		case req@POST -> Root / Group / IntVar(id) => update(groups, req, id)
		case DELETE -> Root / Group / IntVar(id)   => delete(groups, id)
	}

	private val procedureService = HttpService[Task] {
		case GET -> Root / Procedure                   => list(procedures)
		case GET -> Root / Procedure / IntVar(id)      => find(procedures, id)
		case req@POST -> Root / Procedure              => insert(procedures, req)
		case req@POST -> Root / Procedure / IntVar(id) => update(procedures, req, id)
		case DELETE -> Root / Procedure / IntVar(id)   => delete(procedures, id)

		// TODO telemetry and log endpoints
	}

	private val nodeService = HttpService[Task] {
		case GET -> Root / Node                   => list(nodes)
		case GET -> Root / Node / IntVar(id)      => find(nodes, id)
		case req@POST -> Root / Node              => insert(nodes, req)
		case req@POST -> Root / Node / IntVar(id) => update(nodes, req, id)
		case DELETE -> Root / Node / IntVar(id)   => delete(nodes, id)

		// TODO code CRUD

	}


	lazy val services: HttpService[Task] = groupService <+> procedureService <+> nodeService

}
