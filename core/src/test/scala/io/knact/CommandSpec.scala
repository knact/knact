package io.knact

import org.scalatest.FlatSpec

class CommandSpec extends FlatSpec {

	behavior of "Command"

	it should "obey left identity" in {
//		val a: Nothing => Right[Nothing, Int] = _ => Right(1)
//		val f: Int => Command[Nothing, Nothing, Int] = v => Command(_ => Right(v + 1))
//		val x: Command[Nothing, Nothing, Int] = Command(a).flatMap(f)
//		val y: Command[Nothing, Nothing, Int] = f(a)

		//		val a: Int = 1
		//		val f: Int => Some[Int] = x => Some(x + 1)
		//		val x: Option[Int] = Some(a).flatMap(f)
		//		val y: Option[Int] = f(a)


	}

}
