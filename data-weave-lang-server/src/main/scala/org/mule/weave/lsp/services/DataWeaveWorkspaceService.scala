package org.mule.weave.lsp.services

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.commands.CommandProvider
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem

import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/**
 * DataWeave Implementation of the LSP Workspace Service
 *
 */
class DataWeaveWorkspaceService(projectVFS: ProjectVirtualFileSystem,
                                pd: ProjectDefinition,
                                messageLoggerService: MessageLoggerService,
                                toolingService: LSPToolingServices
                               ) extends WorkspaceService {

  private val logger: Logger = Logger.getLogger(getClass.getName)
  private val commandProvider: CommandProvider = new CommandProvider(projectVFS, pd, messageLoggerService, toolingService)

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    logger.log(Level.INFO, "didChangeConfiguration: " + params.getSettings)
    pd.updateSettings(params)
  }

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    logger.log(Level.INFO, "executeCommand: " + params)
    CompletableFuture.supplyAsync(() => {
      commandProvider.commandBy(params.getCommand).map((c) => {
        c.execute(params)
      })
        .orNull
    })
  }


  override def didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams): Unit = {
    logger.log(Level.INFO, "[DataWeaveWorkspaceService] Changed Folders: " + params.getEvent)
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    params.getChanges.forEach((fe) => {
      messageLoggerService.logInfo("[DataWeaveWorkspaceService] Changed Watched File : " + fe.getUri + " - " + fe.getType)
      fe.getType match {
        case FileChangeType.Created => projectVFS.created(fe.getUri)
        case FileChangeType.Changed => projectVFS.changed(fe.getUri)
        case FileChangeType.Deleted => projectVFS.deleted(fe.getUri)
      }
    })
  }
}
