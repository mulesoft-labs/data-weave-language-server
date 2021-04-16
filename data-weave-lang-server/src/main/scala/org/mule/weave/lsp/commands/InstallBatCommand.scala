package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.services.ProjectDefinition

class InstallBatCommand(pd: ProjectDefinition) extends WeaveCommand {
  override def commandId(): String = Commands.BAT_INSTALL_BAT_CLI

  override def execute(params: ExecuteCommandParams): AnyRef = {
    pd.batProjectManager.setupBat()
    null
  }
}
