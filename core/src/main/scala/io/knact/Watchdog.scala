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
  * @param subjects  an observable set of nodes
  * @param transport the transport for the nodes
  */
class Watchdog[A <: Address, C <: Credential, N <: Node](subjects: Observable[Set[Subject[A, C]]],
														 transport: Transport[A, C, N]) {


	type Outcome[R] = (Subject[A, C], Result[R])
	type RepeatedOutput[R] = (Subject[A, C], Long, Result[R])

	/**
	  * Execute a command on all [[subjects]]
	  * @tparam R the return type of the command
	  * @return a monad representing results collected in a list
	  */
	def dispatch[R](c: Command[N, R]): Task[Seq[Outcome[R]]] =
		subjects.mapTask { subjects =>
			Task.wanderUnordered(subjects) { subject =>
				Task {(subject, c.run(transport.connect(subject)))}
			}
		}.firstOrElseL(Nil)

	/**
	  * Execute a command asynchronously and repeatedly on all [[subjects]]
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
							c: Command[N, R]): Observable[RepeatedOutput[R]] =
		bindLatest { subjects =>
			// F[G[A]] >>= { (G[A] => F[A]) >>= { A => (F[B] >>= {B => F[C]}) }}
			Observable.fromIterable(subjects).mergeMap { subject =>
				for {
					id <- Observable.intervalAtFixedRate(interval)
					result <- mkOutcome(id, subject, c)
				} yield result
			}
		}

	/**
	  * Execute a command asynchronously and repeatedly while synchronising at each interval for
	  * all [[subjects]]
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
										c: Command[N, R]): Observable[RepeatedOutput[R]] =
		bindLatest { subjects =>
			for {
				id <- Observable.intervalAtFixedRate(interval)
				result <- Observable.fromIterable(subjects)
					.mergeMap { ss => mkOutcome(id, ss, c) }
			} yield result
		}

	/**
	  * Execute a command asynchronously and repeatedly while synchronising at for each subject for
	  * all [[subjects]]
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
								   c: Command[N, R]): Observable[RepeatedOutput[R]] =
		bindLatest { subjects =>
			for {
				id <- Observable.intervalAtFixedRate(interval)
				subject <- Observable.fromIterable(subjects)
				result <- mkOutcome(id, subject, c)
			} yield result
		}


	private def bindLatest[B](f: Set[Subject[A, C]] => Observable[B]) = subjects.flatMapLatest(f)
	private def mkOutcome[B](id: Long, subject: Subject[A, C], command: Command[N, B]) = {
		// can't compose generic functions, need Observable ~> Observable
		Observable.fork(Observable.eval {(subject, id, command.run(transport.connect(subject)))})
	}

}
