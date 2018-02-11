package io

import cats.data._

package object knact {

	/** Concept of an identity */
	trait Credential {
		def identity: String
		override def toString: String = identity
	}

	/** Concept of a location */
	trait Address {
		def name: String
		override def toString: String = name
	}

	/** Bundles a [[Credential]] and a [[Address]] as in most cases, we need to use them together */
	case class Subject[+A <: Address, +C <: Credential](address: A, credential: C)

	/** Concept of a thing that is at some location and requires authorisation(of some identity) */
	trait Node

	/** Concept of a communication method between a [[Node]] and you */
	trait Transport[-A <: Address, -C <: Credential, +N <: Node] {
		/** Finds, authorises, and returns a [[Node]] of some sort */
		def connect(subject: Subject[A, C]): N
	}


	// XXX
	// Either :: *->*->* , Kleisli needs *->*
	// we can either project it using type lambda: ({ type T[A] = Either[String, A] })#T
	// but type alias is slightly more pleasant

	/**
	  * Represents a failure or a successfully executed command
	  * @tparam T the outcome
	  */
	type Result[T] = Either[String, T]
	object Result {
		/** Constructs a successful result of a command */
		def success[T](t: T): Result[T] = Right(t)
		/** Constructs a failure of a command */
		def failure[T](s: String): Result[T] = Left(s)
	}

	/**
	  * Represents a command that depends on some context [[A]] and returns a [[B]]
	  * @tparam A the context around where the command will be eventually executed
	  * @tparam B the result of executing the command
	  */
	type Command[A <: Node, B] = ReaderT[Result, A, B]
	object Command {
		/**
		  * Lifts a function into a command
		  * @param f the function to be lifted
		  * @tparam A the context
		  * @tparam B the outcome of the function
		  */
		def apply[A <: Node, B](f: A => Result[B]): Command[A, B] = ReaderT(f)
	}

}
