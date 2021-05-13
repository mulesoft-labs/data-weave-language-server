package org.mule.weave.dsp

import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.mule.weave.lsp.IDEExecutors
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.components.ProcessLauncher
import org.mule.weave.lsp.services.ClientLogger
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
             onServerStarted: () => Unit,
             dapPort: Int): Unit = {
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Starting Adapter Protocol Process at ${dapPort}.")
    val serverSocket = new ServerSocket(dapPort)
    onServerStarted()
    val socket = serverSocket.accept()
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Connection established.")
    val in: InputStream = socket.getInputStream
    val out: OutputStream = socket.getOutputStream
    val testDebugServer = new DataWeaveDebuggerProtocolAdapter(virtualFileSystem, clientLogger, languageClient, processLauncher, IDEExecutors.debuggingExecutor())
    val launcher = DSPLauncher.createServerLauncher(testDebugServer, in, out)
    testDebugServer.connect(launcher.getRemoteProxy)
    launcher.startListening
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Debugger Adapter Finished.")
  }

}

