package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.services.ProjectDefinition

import scala.collection.JavaConverters._
import scala.collection.mutable

class RunBatTestCommand(pd: ProjectDefinition) extends WeaveCommand {
  override def commandId(): String = Commands.BAT_RUN_BAT_TEST

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val arguments: mutable.Seq[AnyRef] = params.getArguments.asScala
    pd.batProjectManager.run(arguments.head.toString, arguments.tail.headOption.map(_.toString))
  }
}
