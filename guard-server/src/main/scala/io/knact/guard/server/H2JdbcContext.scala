package io.knact.guard.server
import java.time.ZonedDateTime

import io.knact.guard._

class H2JdbcContext(override val startTime: ZonedDateTime)  extends ApiContext {

	// TODO write me

	override def groups: GroupRepository = ???
	override def nodes: NodeRepository = ???
	override def procedures: ProcedureRepository = ???


}
