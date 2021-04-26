package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.dsp.DataWeaveDebuggerAdapterProtocolLauncher
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors

//LSP .....
// LaunchDebugger
// [VSCODE] <-> [LSPADI] <-> [PDW]

class LaunchDebuggerCommand(virtualFileSystem: VirtualFileSystem, messageLoggerService: ClientLogger) extends WeaveCommand {

  override def commandId(): String = Commands.DW_RUN_DEBUGGER

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val port: Int = freePort()
    val executorService = Executors.newSingleThreadExecutor();
    executorService.submit(new Runnable {
      override def run(): Unit = {
        DataWeaveDebuggerAdapterProtocolLauncher.launch(virtualFileSystem, messageLoggerService, port)
      }
    })
    new Integer(port)
  }


  @throws[IOException]
  private def freePort() = {
    try {
      val socket = new ServerSocket(0)
      try {
        socket.getLocalPort
      } finally {
        if (socket != null) {
          socket.close()
        }
      }
    }
  }
}
