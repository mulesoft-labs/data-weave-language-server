package org.mule.weave.lsp.commands

import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

class CommandProvider(virtualFileSystem: VirtualFileSystem,
                      projectVirtualFileSystem: ProjectVirtualFileSystem,
                      clientLogger: ClientLogger,
                      languageClient: WeaveLanguageClient,
                      project: Project,
                      projectKind: ProjectKind,
                      jobManagerService: JobManagerService,
                      validationService: DataWeaveToolingService,
                      previewService: PreviewService
                     ) {

  val commands = Seq(
    new RunBatTestCommand(clientLogger),
    new RunBatFolderTestCommand(clientLogger),
    new CreateSampleDataCommand(projectKind, languageClient),
    new CreateTestCommand(projectKind, languageClient),
    new EnablePreviewModeCommand(previewService, virtualFileSystem),
    new RunPreviewCommand(previewService, virtualFileSystem),
    new InstallBatCommand(clientLogger),
    new RunWeaveCommand(virtualFileSystem, projectVirtualFileSystem, project, projectKind, clientLogger, jobManagerService, languageClient),
    new LaunchWeaveCommand(languageClient),
    new QuickFixCommand(validationService),
    new InsertDocumentationCommand(validationService)
  )

  def commandBy(commandId: String): Option[WeaveCommand] = {
    commands.find(_.commandId() == commandId)
  }

}
