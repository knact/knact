package io.knact.guard.server

import io.circe._
import io.knact.guard.Entity
import io.knact.guard.Entity._
import io.knact.guard.server.TaskAssertions.SyncContext
import monix.eval.Task
import org.scalatest.{EitherValues, FlatSpec, Matchers}

class RepositorySpec extends FlatSpec with Matchers with EitherValues {

	behavior of "GroupRepository"

	it should "insert with incremented id" in SyncContext {
		val imr = new InMemoryRepository()
		val group = Group(id(42), "foo", Nil)
		for {
			a <- imr.groups.insert(group)
			b <- imr.groups.insert(group)
		} yield {
			a.right.value shouldBe id(0)
			b.right.value shouldBe id(1)
		}
	}

	it should "find inserted items" in SyncContext {
		val imr = new InMemoryRepository()
		for {
			id <- imr.groups.insert(Group(id(42), "foo", Nil))
			found <- imr.groups.find(id.right.value)
		} yield found contains Group(Entity.id(0), "foo", Nil)
	}

	it should "insert discards id and relations" in SyncContext {
		val imr = new InMemoryRepository()
		for {
			id <- imr.groups.insert(Group(id(42), "bar", Seq(id(42), id(43))))
			found <- imr.groups.find(id.right.value)
		} yield found contains Group(Entity.id(0), "bar", Nil)
	}

	it should "list all ids in insert order" in SyncContext {
		val imr = new InMemoryRepository()
		val groups = List.fill(10) {Group(id(42), "foo", Nil)}
		for {
			ids <- Task.traverse(groups) {imr.groups.insert}
			gs <- imr.groups.list()
		} yield gs should contain theSameElementsInOrderAs ids.map {_.right.value}
	}

	it should "update" in {
		// TODO write me
	}

	it should "delete" in {
		// TODO write me
	}


}