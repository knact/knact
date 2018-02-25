package io

import cats.data._
import monix.eval.Task

package object knact {


	trait Connectable[A, B] {
		def connect(a: A): Task[B]
	}


	// XXX
	// Either :: *->*->* , Kleisli needs *->*
	// we can either project it using type lambda: ({ type T[A] = Either[String, A] })#T
	// but type alias is slightly more pleasant

	/**
	  * Represents a failure or a successfully executed command
	  * @tparam T the outcome
	  */
	type Result[T] = Either[Throwable, T]
	object Result {
		/** Constructs a successful result of a command */
		def success[T](t: T): Result[T] = Right(t)
		/** Constructs a failure of a command */
		def failure[T](s: Throwable): Result[T] = Left(s)
		def failure[T](s: String): Result[T] = Left(new Exception(s))
	}

	/**
	  * Represents a command that depends on some context [[A]] and returns a [[B]]
	  * @tparam A the context around where the command will be eventually executed
	  * @tparam B the result of executing the command
	  */
	type Command[A, B] = ReaderT[Result, A, B]
	object Command {
		/**
		  * Lifts a function into a command
		  * @param f the function to be lifted
		  * @tparam A the context
		  * @tparam B the outcome of the function
		  */
		def apply[A, B](f: A => Result[B]): Command[A, B] = ReaderT(f)
	}

}
