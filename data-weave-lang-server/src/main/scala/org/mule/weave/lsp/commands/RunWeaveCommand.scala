package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.mule.weave.dsp.DataWeaveDebuggerAdapterProtocolLauncher.launch
import org.mule.weave.lsp.IDEExecutors
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.DefaultWeaveLauncher
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.v2.editor.VirtualFileSystem

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

//LSP .....
// LaunchDebugger
// [VSCODE] <-> [LSPADI] <-> [PDW]

class RunWeaveCommand(virtualFileSystem: VirtualFileSystem,
                      project: Project,
                      projectKind: ProjectKind,
                      clientLogger: ClientLogger,
                      languageClient: WeaveLanguageClient) extends WeaveCommand {

  override def commandId(): String = Commands.DW_RUN_MAPPING

  override def execute(params: ExecuteCommandParams): AnyRef = {
    if (!project.initialized()) {
      languageClient.showMessage(new MessageParams(MessageType.Warning, "Can not run a DW script until Project was initialized."))
      new Integer(-1)
    } else {
      val port: Int = freePort()
      val latch = new CountDownLatch(1)
      IDEExecutors.defaultExecutor().submit(new Runnable {
        override def run(): Unit = {
          launch(virtualFileSystem, clientLogger, languageClient, new DefaultWeaveLauncher(projectKind, languageClient), () => latch.countDown(), port)
        }
      })
      latch.await()
      new Integer(port)
    }
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
