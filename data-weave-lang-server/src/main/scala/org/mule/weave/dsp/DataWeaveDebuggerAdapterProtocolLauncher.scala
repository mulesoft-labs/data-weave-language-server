package org.mule.weave.dsp

import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.mule.weave.lsp.IDEExecutors
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProcessLauncher
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveTestService
import org.mule.weave.v2.editor.VirtualFileSystem

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.logging.Level
import java.util.logging.Logger

/**
  * Starts The DAP Server
  */
object DataWeaveDebuggerAdapterProtocolLauncher {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  def launch(virtualFileSystem: VirtualFileSystem,
             clientLogger: ClientLogger,
             languageClient: WeaveLanguageClient,
             processLauncher: ProcessLauncher,
             projectKind: ProjectKind,
             jobManagerService: JobManagerService,
             dataWeaveTestService: DataWeaveTestService,
             onServerStarted: () => Unit,
             dapPort: Int): Unit = {
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Starting Adapter Protocol Process at ${dapPort}.")
    val serverSocket = new ServerSocket(dapPort)
    onServerStarted()
    val socket = serverSocket.accept()
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Connection established.")
    val in: InputStream = socket.getInputStream
    val out: OutputStream = socket.getOutputStream
    val testDebugServer: DataWeaveDebuggerProtocolAdapter =
      new DataWeaveDebuggerProtocolAdapter(
        virtualFileSystem = virtualFileSystem,
        clientLogger = clientLogger,
        languageClient = languageClient,
        launcher = processLauncher,
        projectKind = projectKind,
        executor = IDEExecutors.debuggingExecutor(),
        jobManagerService = jobManagerService,
        weaveTestManager = dataWeaveTestService)

    val launcher = DSPLauncher.createServerLauncher(testDebugServer, in, out)
    testDebugServer.connect(launcher.getRemoteProxy)
    launcher.startListening
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Debugger Adapter Finished.")
  }

}

