package org.mule.weave.lsp.services

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.dsp.DataWeaveDebuggerAdapterProtocolLauncher
import org.mule.weave.lsp.Commands
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem

import scala.collection.JavaConverters._

class DataWeaveWorkspaceService(projectVFS: ProjectVirtualFileSystem, pd: ProjectDefinition, logger: MessageLoggerService) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    logger.logInfo("[DataWeaveWorkspaceService] Changed Configuration: " + params.getSettings)
    pd.updateSettings(params)
  }

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    logger.logInfo("[DataWeaveWorkspaceService] executeCommand: " + params)
    var result: AnyRef = null
    val command = params.getCommand
    if (command == Commands.BAT_RUN_BAT_TEST) {
      val arguments = params.getArguments.asScala
      pd.batProjectManager.run(arguments.head.toString, arguments.tail.headOption.map(_.toString))
    } else if (command == Commands.BAT_RUN_BAT_FOLDER) {
      val arguments = params.getArguments.asScala
      pd.batProjectManager.run(arguments.head.toString, None)
    } else if (command == Commands.BAT_INSTALL_BAT_CLI) {
      pd.batProjectManager.setupBat()
    } else if (command == Commands.DW_RUN_DEBUGGER) {
      val port = freePort()
      val executorService = Executors.newSingleThreadExecutor();
      executorService.submit(new Runnable {
        override def run(): Unit = {
          DataWeaveDebuggerAdapterProtocolLauncher.launch(projectVFS, logger, port)
        }
      })
      result = new Integer(port)
    }
    CompletableFuture.completedFuture(result)
  }


  @throws[IOException]
  private def freePort() = try {
    val socket = new ServerSocket(0)
    try {
      socket.getLocalPort
    }
    finally {
      if (socket != null) socket.close()
    }
  }

  override def didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams): Unit = {
    println("[DataWeaveWorkspaceService] Changed Folders: " + params.getEvent)
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    params.getChanges.forEach((fe) => {
      logger.logInfo("[DataWeaveWorkspaceService] Changed Watched File : " + fe.getUri + " - " + fe.getType)
      fe.getType match {
        case FileChangeType.Created => projectVFS.created(fe.getUri)
        case FileChangeType.Changed => projectVFS.changed(fe.getUri)
        case FileChangeType.Deleted => projectVFS.deleted(fe.getUri)
      }
    })
  }
}
