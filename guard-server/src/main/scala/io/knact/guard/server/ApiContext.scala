package io.knact.guard.server

import java.time.ZonedDateTime

import io.knact.guard.{NodeRepository, ProcedureRepository}

trait ApiContext {

	def version: String
	def startTime: ZonedDateTime

	def nodes: NodeRepository
	def procedures: ProcedureRepository

	def repos: (NodeRepository, ProcedureRepository) = (nodes, procedures)

}
