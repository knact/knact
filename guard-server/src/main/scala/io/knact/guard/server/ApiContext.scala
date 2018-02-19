package io.knact.guard.server

import io.knact.guard.{GroupRepository, NodeRepository, ProcedureRepository}

trait ApiContext {

	def groups: GroupRepository
	def nodes: NodeRepository
	def procedures: ProcedureRepository

	def repos: (GroupRepository, NodeRepository, ProcedureRepository) =
		(groups, nodes, procedures)

}
