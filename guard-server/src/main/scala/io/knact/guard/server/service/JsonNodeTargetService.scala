package io.knact.guard.server.service

import better.files.File
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe._
import io.circe.parser.decode
import io.circe.java8.time._
import io.circe.generic.auto._
import io.knact.guard.Entity.{Id, Node, Target, id}
import io.knact.guard.NodeRepository
import io.knact.guard.server.component.ObservableFileMonitor
import io.knact.guard.server.component.ObservableFileMonitor.WatchMessage
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.duration._
import monix.execution.Scheduler.Implicits.global

object JsonNodeTargetService extends LazyLogging {


	private def readTargets(file: File): Observable[Either[Throwable, List[Target]]] = Observable.fromTask(Task {
		logger.info(s"Target file changed, reloading")
		decode[List[Target]](file.contentAsString)
	}.onErrorHandle(Left(_)))

	def apply(file: File, repo: NodeRepository): Task[Unit] = Task {
		// TODO needs ARM i.e brackets
		val mon = new ObservableFileMonitor()
		mon.registerPath(file.parentOption.getOrElse(file))
		val targets = readTargets(file) ++ mon.observable
			.collect { case WatchMessage(_, that) if that == file => file }
			.debounce(200 millis)
			.flatMapLatest {readTargets}

		targets.flatMapLatest {
			case Left(ex)  =>
				logger.warn(s"Failed to load targets($targets)", ex)
				Observable.pure(())
			case Right(ts) =>
				logger.info(s"Loaded ${ts.length} nodes")
				Observable.fromTask(for {
					delta <- for {
						ids <- repo.list()
						search <- Task.wanderUnordered(ts) { t => repo.find(t).map {t -> _} }
					} yield {
						val keep: Seq[Node] = search.collect { case (_, Some(n)) => n }
						val add: Seq[Target] = search.collect { case (t, None) => t }
						val remove: Seq[Id[Node]] = ids.diff(keep.map{_.id})
						logger.info(s"keeping  $keep")
						logger.info(s"removing $remove")
						logger.info(s"adding $add")
						(add, remove)
					}
					(add, remove) = delta
					_ <- Task.wanderUnordered(add.map { t => Node(id(0), t, "") }) {repo.insert}
					_ <- Task.wanderUnordered(remove) {repo.delete}
				} yield ())
		}.executeAsync.subscribe()
		()
	}


}
