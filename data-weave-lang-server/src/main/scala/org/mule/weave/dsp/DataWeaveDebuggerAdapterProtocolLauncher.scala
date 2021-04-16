package org.mule.weave.dsp

import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.v2.editor.VirtualFileSystem

import java.net.ServerSocket
import java.util.logging.Level
import java.util.logging.Logger

object DataWeaveDebuggerAdapterProtocolLauncher {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  def launch(virtualFileSystem: VirtualFileSystem, messageLoggerService: MessageLoggerService, port: Int) {
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Starting Adapter Protocol Process at ${port}.")
    val serverSocket = new ServerSocket(port)
    val socket = serverSocket.accept()
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Connection established.")
    val in = socket.getInputStream
    val out = socket.getOutputStream
    val testDebugServer = new DataWeaveDebuggerProtocolAdapter(virtualFileSystem, messageLoggerService)
    val launcher = DSPLauncher.createServerLauncher(testDebugServer, in, out)
    testDebugServer.connect(launcher.getRemoteProxy)
    launcher.startListening
    logger.log(Level.INFO, s"[DataWeaveDebuggerAdapterProtocolLauncher] Debugger Adapter Finished.")
  }

}
