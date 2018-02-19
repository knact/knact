package io.knact.guard.server

import io.knact.guard.{GroupRepository, NodeRepository, ProcedureRepository}

trait ApiDependency {

	def groups: GroupRepository
	def nodes: NodeRepository
	def procedures: ProcedureRepository

	def asTuple: (GroupRepository, NodeRepository, ProcedureRepository) =
		(groups, nodes, procedures)

}
