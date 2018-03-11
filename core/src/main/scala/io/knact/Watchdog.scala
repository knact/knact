package io.knact

import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.duration.FiniteDuration


/**
  * A watchdog service for sending periodic commands to nodes
  *
  * ==Continuity==
  * For any method that returns a sequence of some sort(i.e [[dispatchRepeated]]), the sequence
  * number is not preserved over subject changes, that is, even if the exact same subjects was
  * supplied, they will all start from 0.
  *
  * ==Threading==
  * Regardless of method, no [[io.knact.Command]] will ever execute on the calling thread of
  * this service. The implied threading means that no commands will return synchronously so every
  * method either return a [[monix.eval.Task]] monad or a [[monix.reactive.Observable]] container.
  *
  * @param nodes an observable set of nodes
  */
class Watchdog[A, B](nodes: Observable[Set[A]])(implicit ev: Connectable[A, B]) {


	type Outcome[R] = (A, Result[R])
	type RepeatedOutput[R] = (Long, A, Result[R])


	/**
	  * Execute a command on all [[nodes]]
	  * @tparam R the return type of the command
	  * @return a monad representing results collected in a list
	  */
	def dispatch[R](command: Command[B, R]): Task[Seq[Outcome[R]]] =
		nodes.mapTask { as =>
			Task.wanderUnordered(as) { a =>
				(for {
					n <- ev.connect(a)
					r <- Task {command.run(n)}
				} yield (a, r)).onErrorHandle(e => (a, Result.failure(e)))
			}
		}.firstOrElseL(Nil)

	/**
	  * Execute a command asynchronously and repeatedly on all [[nodes]]
	  * To illustrate, given subject `A, B, C`:
	  * {{{
	  * A ----> A --> A ---> A --> ...
	  * -B -> B -> B ------------> ...
	  * --C -------------- C ------> ...
	  * }}}
	  * Which translates to the following sequence
	  * {{{
	  * A->B->C->B->A->B->A->C->A
	  * }}}
	  *
	  * When a command's execution time is longer than the interval, the the next command will
	  * wait indefinitely for the current one to complete.
	  *
	  * @tparam R the return type of the command
	  * @return a observable with sequenced output
	  */
	def dispatchRepeated[R](interval: FiniteDuration,
							command: Command[B, R]): Observable[RepeatedOutput[R]] =
		bindLatest { as =>
			// F[G[A]] >>= { (G[A] => F[A]) >>= { A => (F[B] >>= {B => F[C]}) }}
			Observable.fromIterable(as).mergeMap { a =>
				for {
					id <- Observable.intervalAtFixedRate(interval)
					r <- mkOutcome(id, a, command)
				} yield r
			}
		}

	/**
	  * Execute a command asynchronously and repeatedly while synchronising at each interval for
	  * all [[nodes]]
	  * To illustrate, given subject `A, B, C`:
	  * {{{
	  * A ---->          A ------> A ->         ...
	  * B -->            B ->      B ---------> ...
	  * C -------------- C --->    C ->         ...
	  * }}}
	  * Which translates to the following sequence
	  * {{{
	  * A->B->C-A->B->C-A->B->C
	  * }}}
	  * When a command's execution time is longer than the interval, the the next command will
	  * wait indefinitely for the current one to complete.
	  *
	  * @tparam R the return type of the command
	  * @return a observable with sequenced output
	  */
	def dispatchRepeatedSyncInterval[R](interval: FiniteDuration,
										command: Command[B, R]): Observable[RepeatedOutput[R]] =
		bindLatest { as =>
			for {
				id <- Observable.intervalAtFixedRate(interval)
				r <- Observable.fromIterable(as).mergeMap { a => mkOutcome(id, a, command) }
			} yield r
		}

	/**
	  * Execute a command asynchronously and repeatedly while synchronising at for each subject for
	  * all [[nodes]]
	  * To illustrate, given subject `A, B, C`:
	  * {{{
	  * A ----> B ---------> C ---> A --> B --> C
	  * }}}
	  * Which translates to the following sequence
	  * {{{
	  * A->B->C-A->B->C
	  * }}}
	  * When a command's execution time is longer than the interval, the the next command will
	  * wait indefinitely for the current one to complete.
	  *
	  * @tparam R the return type of the command
	  * @return a observable with sequenced output
	  */
	def dispatchRepeatedSyncAll[R](interval: FiniteDuration,
								   command: Command[B, R]): Observable[RepeatedOutput[R]] =
		bindLatest { as =>
			for {
				id <- Observable.intervalAtFixedRate(interval)
				a <- Observable.fromIterable(as)
				r <- mkOutcome(id, a, command)
			} yield r
		}


	private def bindLatest[C](f: Set[A] => Observable[C]) = nodes.flatMapLatest(f)
	private def mkOutcome[C](id: Long, a: A, command: Command[B, C]) = {
		Observable.fork(Observable.fromTask(for {
			n <- ev.connect(a)
			r <- Task {command.run(n)}
		} yield (id, a, r))).onErrorHandle(e => (id, a, Result.failure(e)))
	}

}
