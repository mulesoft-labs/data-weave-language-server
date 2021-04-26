package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.bat.BatProjectHelper
import org.mule.weave.lsp.services.ClientLogger

import scala.collection.JavaConverters._

class RunBatFolderTestCommand(cl: ClientLogger) extends WeaveCommand {
  override def commandId(): String = Commands.BAT_RUN_BAT_FOLDER

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val arguments = params.getArguments.asScala
    val batProjectManager = new BatProjectHelper(cl)
    batProjectManager.run(arguments.head.toString, None)
  }
}
