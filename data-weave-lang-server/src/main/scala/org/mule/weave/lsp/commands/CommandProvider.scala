package org.mule.weave.lsp.commands

import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.ValidationServices
import org.mule.weave.v2.editor.VirtualFileSystem

class CommandProvider(virtualFileSystem: VirtualFileSystem,
                      clientLogger: ClientLogger,
                      toolingService: ValidationServices) {

  val commands = Seq(
    new RunBatTestCommand(clientLogger),
    new RunBatFolderTestCommand(clientLogger),
    new InstallBatCommand(clientLogger),
    new LaunchDebuggerCommand(virtualFileSystem, clientLogger),
    new QuickFixCommand(toolingService),
    new InsertDocumentationCommand(toolingService)
  )

  def commandBy(commandId: String): Option[WeaveCommand] = {
    commands.find(_.commandId() == commandId)
  }

}
