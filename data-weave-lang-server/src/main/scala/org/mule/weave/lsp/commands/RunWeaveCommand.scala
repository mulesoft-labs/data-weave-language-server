package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.mule.weave.dsp.DataWeaveDebuggerAdapterProtocolLauncher.launch
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.jobs.Status
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProcessLauncher
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveTestService
import org.mule.weave.lsp.utils.NetUtils
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

import java.util.concurrent.CountDownLatch

//LSP .....
// LaunchDebugger
// [VSCODE] <-> [LSPADI] <-> [PDW]

class RunWeaveCommand(virtualFileSystem: VirtualFileSystem,
                      projectVirtualFileSystem: ProjectVirtualFileSystem,
                      project: Project,
                      projectKind: ProjectKind,
                      clientLogger: ClientLogger,
                      jobManagerService: JobManagerService,
                      languageClient: WeaveLanguageClient,
                      dataWeaveTestService: DataWeaveTestService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_RUN_MAPPING

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val configType = Commands.argAsString(params.getArguments, 0)
    runMapping(configType)
  }

  def runMapping(config: String): Integer = {
    if (!project.isStarted()) {
      languageClient.showMessage(new MessageParams(MessageType.Warning, "Can not run a DW script until Project was initialized."))
      -1
    } else {
      val port: Int = NetUtils.freePort()
      val latch = new CountDownLatch(1)
      jobManagerService.schedule((status: Status) => {
        val launcher: ProcessLauncher = ProcessLauncher.createLauncherByType(config, projectKind, clientLogger, languageClient, projectVirtualFileSystem)
        launch(virtualFileSystem, clientLogger, languageClient, launcher, projectKind, jobManagerService, dataWeaveTestService, () => latch.countDown(), port)
      }, "Starting Debugger Server", "Starting Debugger Server")
      latch.await()
      port
    }
  }


}
