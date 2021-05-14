package org.mule.weave.lsp.commands

import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.ValidationService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

class CommandProvider(virtualFileSystem: VirtualFileSystem,
                      projectVirtualFileSystem: ProjectVirtualFileSystem,
                      clientLogger: ClientLogger,
                      languageClient: WeaveLanguageClient,
                      project: Project,
                      projectKind: ProjectKind,
                      validationService: ValidationService) {

  val commands = Seq(
    new RunBatTestCommand(clientLogger),
    new RunBatFolderTestCommand(clientLogger),
    new CreateSampleData(projectKind, languageClient),
    new InstallBatCommand(clientLogger),
    new RunWeaveCommand(virtualFileSystem, projectVirtualFileSystem, project, projectKind, clientLogger, languageClient),
    new QuickFixCommand(validationService),
    new InsertDocumentationCommand(validationService)
  )

  def commandBy(commandId: String): Option[WeaveCommand] = {
    commands.find(_.commandId() == commandId)
  }

}
