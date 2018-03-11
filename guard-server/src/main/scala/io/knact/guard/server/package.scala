package io.knact.guard

import monix.execution.Scheduler

package object server {

	implicit val scheduler: Scheduler = Scheduler.forkJoin(
		name = "guard",
		parallelism = sys.runtime.availableProcessors(),
		maxThreads = sys.runtime.availableProcessors())
//	implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

}
