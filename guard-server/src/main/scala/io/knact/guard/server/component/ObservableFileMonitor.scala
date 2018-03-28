package io.knact.guard.server.component

import java.nio.file.StandardWatchEventKinds._
import java.nio.file._

import better.files.File
import io.knact.guard.server.component.ObservableFileMonitor.{Created, Deleted, Modified, WatchMessage}
import monix.execution.Scheduler.Implicits.global
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{MulticastStrategy, Observable}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try


object ObservableFileMonitor {

	sealed trait Event
	case object Created extends Event
	case object Modified extends Event
	case object Deleted extends Event

	case class WatchMessage(kind: Event, file: File)
}

class ObservableFileMonitor {

	private val watchService = FileSystems.getDefault.newWatchService()
	private val termination  = ConcurrentSubject(MulticastStrategy.replay[Nothing])

	val observable: Observable[WatchMessage] = Observable
		.repeatEval {Try {watchService.take()}.toOption}
		.takeUntil(termination)
		.flatMap { maybeKey =>
			val events = (for {
				key <- maybeKey.toSeq
				event <- key.pollEvents().asScala
			} yield (key, event)).collect {
				// java's watch service is not typed
				case (key, event: WatchEvent[Path]@unchecked) =>
					val path = key.watchable.asInstanceOf[Path]
					val target: Path = path.resolve(event.context)
					(1 to event.count()).map { _ =>
						WatchMessage(event.kind() match {
							case ENTRY_CREATE => Created
							case ENTRY_DELETE => Deleted
							case ENTRY_MODIFY => Modified
							case unknown      =>
								throw new AssertionError(s"Unexpected event type $unknown")
						}, target)
					}
				case _                                        => Nil // TODO log this?
			}.flatten
			maybeKey.foreach {_.reset()}
			Observable.fromIterable(events)
		}
		.doOnComplete { () => watchService.close() }
		.share.subscribeOn(global)

	def stop(): Unit = {
		termination.onComplete()
	}

	// TODO bad
	private val map = mutable.HashMap.empty[File, WatchKey]

	def registerPath(file: File): Try[File] = Try {
		if (file.isDirectory) {
			if (!map.contains(file)) map + (file -> file.path.register(watchService,
				ENTRY_MODIFY,
				ENTRY_DELETE,
				ENTRY_CREATE))
			file
		} else {
			throw new UnsupportedOperationException(s"Path $file is not a directory")
		}
	}

	def unregisterPath(file: File): Try[File] = Try {
		map.get(file) match {
			case Some(value) => value.cancel(); file
			case None        =>
				throw new NoSuchElementException(s"Path $file was not previously registered")
		}
	}

}
