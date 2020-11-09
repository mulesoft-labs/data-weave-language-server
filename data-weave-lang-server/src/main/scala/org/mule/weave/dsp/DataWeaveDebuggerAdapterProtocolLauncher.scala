package org.mule.weave.dsp

import java.net.ServerSocket

import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.v2.editor.VirtualFileSystem

object DataWeaveDebuggerAdapterProtocolLauncher {

  def launch( virtualFileSystem: VirtualFileSystem, logger: MessageLoggerService, port: Int) {
    println(s"[DataWeaveDebuggerAdapterProtocolLauncher] Starting Adapter Protocol Process at ${port}.")
    val serverSocket = new ServerSocket(port)
    val socket = serverSocket.accept()
    println(s"[DataWeaveDebuggerAdapterProtocolLauncher] Connection established.")
    val in = socket.getInputStream
    val out = socket.getOutputStream
    val testDebugServer = new DataWeaveDebuggerProtocolAdapter(virtualFileSystem, logger)
    val launcher = DSPLauncher.createServerLauncher(testDebugServer, in, out)
    testDebugServer.connect(launcher.getRemoteProxy)
    launcher.startListening
    println(s"[DataWeaveDebuggerAdapterProtocolLauncher] Debugger Adapter Finished.")
  }

}
