package org.mule.weave.lsp.commands

import org.mule.weave.lsp.services.LSPToolingServices
import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.lsp.services.ProjectDefinition
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem

class CommandProvider(projectVFS: ProjectVirtualFileSystem,
                      pd: ProjectDefinition,
                      messageLoggerService: MessageLoggerService,
                      toolingService: LSPToolingServices) {

  val commands = Seq(
    new RunBatTestCommand(pd),
    new RunBatFolderTestCommand(pd),
    new InstallBatCommand(pd),
    new LaunchDebuggerCommand(projectVFS, messageLoggerService),
    new QuickFixCommand(toolingService),
    new InsertDocumentationCommand(toolingService)
  )

  def commandBy(commandId: String): Option[WeaveCommand] = {
    commands.find(_.commandId() == commandId)
  }

}
