package io.knact.guard.server

import java.time.ZonedDateTime

import io.circe._
import io.knact.guard.Entity
import io.knact.guard.Entity._
import io.knact.guard.server.TaskAssertions.SyncContext
import monix.eval.Task
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

class RepositorySpec extends FlatSpec with Matchers with EitherValues {

	behavior of "NodeRepository"

	private def mkContext(): InMemoryContext = new InMemoryContext("0.0.1", ZonedDateTime.now())

	forAll(Table("repo",
		() => new InMemoryContext("0.0.1", ZonedDateTime.now()): ApiContext
	)) { f =>

		val target = SshPasswordTarget("a", 42, "a", "a")

		it should "insert with incremented id" in SyncContext {
			val ctx = f()
			val node = Node(id(42), target, "foo")
			for {
				a <- ctx.nodes.insert(node)
				b <- ctx.nodes.insert(node)
			} yield {
				a.right.value shouldBe id(0)
				b.right.value shouldBe id(1)
			}
		}

		it should "find inserted items" in SyncContext {
			val ctx = f()
			for {
				id <- ctx.nodes.insert(Node(id(42), target, "foo"))
				found <- ctx.nodes.find(id.right.value)
			} yield found contains Node(Entity.id(0), target, "foo")
		}

		it should "insert discards id and relations" in SyncContext {
			val ctx = f()
			for {
				id <- ctx.nodes.insert(Node(id(42), target, "bar", Map(), Map()))
				found <- ctx.nodes.find(id.right.value)
			} yield found contains Node(Entity.id(0), target, "bar")
		}

		it should "list all ids in insert order" in SyncContext {
			val ctx = f()
			val nodes = List.fill(10) {Node(id(42), target, "foo")}
			for {
				ids <- Task.traverse(nodes) {ctx.nodes.insert}
				gs <- ctx.nodes.list()
			} yield gs should contain theSameElementsInOrderAs ids.map {_.right.value}
		}

		it should "update" in {
			val ctx = f()
			// TODO write me
		}

		it should "delete" in {
			val ctx = f()
			// TODO write me
		}
	}


}