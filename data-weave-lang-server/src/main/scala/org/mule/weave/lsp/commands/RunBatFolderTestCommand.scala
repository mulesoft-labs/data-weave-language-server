package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.services.ProjectDefinition
import scala.collection.JavaConverters._

class RunBatFolderTestCommand(pd: ProjectDefinition) extends WeaveCommand {
  override def commandId(): String = Commands.BAT_RUN_BAT_FOLDER

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val arguments = params.getArguments.asScala
    pd.batProjectManager.run(arguments.head.toString, None)
  }
}
