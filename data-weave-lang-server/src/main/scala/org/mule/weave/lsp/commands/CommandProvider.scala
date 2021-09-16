package org.mule.weave.lsp.commands

import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveTestService
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

class CommandProvider(virtualFileSystem: VirtualFileSystem,
                      projectVirtualFileSystem: ProjectVirtualFileSystem,
                      clientLogger: ClientLogger,
                      languageClient: WeaveLanguageClient,
                      project: Project,
                      projectKind: ProjectKind,
                      jobManagerService: JobManagerService,
                      weaveToolingService: DataWeaveToolingService,
                      scenariosManager: WeaveScenarioManagerService,
                      previewService: PreviewService,
                      dataWeaveTestService: DataWeaveTestService
                     ) {

  val commands = Seq(
    new RunBatTestCommand(clientLogger),
    new RunBatFolderTestCommand(clientLogger),
    new CreateScenarioCommand(languageClient, scenariosManager),
    new DeleteScenarioCommand(scenariosManager),
    new SetActiveScenarioCommand(scenariosManager),
    new DeleteInputSampleCommand(scenariosManager),
    new CreateInputSampleCommand(languageClient, scenariosManager),
    new SaveOutputCommand(languageClient, scenariosManager, virtualFileSystem, previewService),
    new DeleteOutputSampleCommand(scenariosManager),
    new SetActiveScenarioCommand(scenariosManager),
    new CreateTestCommand(projectKind, languageClient),
    new CreateMappingFileCommand(projectKind, languageClient),
    new CreateModuleFileCommand(projectKind, languageClient),
    new EnablePreviewModeCommand(previewService, virtualFileSystem),
    new RunPreviewCommand(previewService, virtualFileSystem),
    new InstallBatCommand(clientLogger),
    new RunWeaveCommand(virtualFileSystem, projectVirtualFileSystem, project, projectKind, clientLogger, jobManagerService, languageClient, dataWeaveTestService),
    new LaunchWeaveCommand(languageClient),
    new LaunchWeaveTestCommand(languageClient, virtualFileSystem),
    new QuickFixCommand(weaveToolingService),
    new InsertDocumentationCommand(weaveToolingService),
    new InsertWeaveTypeCommand(weaveToolingService, project),
    new ExtractVariableCommand(weaveToolingService),
    new CreateUnitTest(weaveToolingService, languageClient, projectKind),
  )

  def commandBy(commandId: String): Option[WeaveCommand] = {
    commands.find(_.commandId() == commandId)
  }

}
