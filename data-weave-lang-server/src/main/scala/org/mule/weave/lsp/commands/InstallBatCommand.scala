package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.bat.BatProjectHelper
import org.mule.weave.lsp.services.ClientLogger

class InstallBatCommand(cl: ClientLogger) extends WeaveCommand {
  override def commandId(): String = Commands.BAT_INSTALL_BAT_CLI

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val batProjectManager = new BatProjectHelper(cl)
    batProjectManager.setupBat()
    null
  }
}
