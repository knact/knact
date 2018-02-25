package io.knact.guard.server

import java.time.ZonedDateTime

import io.knact.guard.{GroupRepository, NodeRepository, ProcedureRepository}

trait ApiContext {

	def version: String
	def startTime: ZonedDateTime

	def groups: GroupRepository
	def nodes: NodeRepository
	def procedures: ProcedureRepository

	def repos: (GroupRepository, NodeRepository, ProcedureRepository) =
		(groups, nodes, procedures)

}
