package org.mule.weave.lsp.services

import java.util
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem

import scala.collection.JavaConverters._

class DataWeaveWorkspaceService(projectVFS: ProjectVirtualFileSystem, pd: ProjectDefinition, logger: MessageLoggerService) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    logger.logInfo("[DataWeaveWorkspaceService] Changed Configuration: " + params.getSettings)
    pd.updateSettings(params)
  }

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    logger.logInfo("[DataWeaveWorkspaceService] executeCommand: " + params)
    val command = params.getCommand
    if(command == "bat.runCurrentBatTest"){
      val arguments = params.getArguments.asScala
      pd.batProjectManager.run(arguments.head.toString, arguments.tail.headOption.map(_.toString))
    } else if(command == "bat.runFolder"){
      val arguments = params.getArguments.asScala
      pd.batProjectManager.run(arguments.head.toString, None)
    } else if(command == "bat.installCli"){
      pd.batProjectManager.setupBat()
    }
    CompletableFuture.completedFuture(null)
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
