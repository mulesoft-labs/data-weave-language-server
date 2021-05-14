package org.mule.weave.lsp.services

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.commands.CommandProvider
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/**
 * DataWeave Implementation of the LSP Workspace Service
 *
 */
class DataWeaveWorkspaceService(
                                 project: Project,
                                 projectKind: ProjectKind,
                                 eventBus: EventBus,
                                 vfs: VirtualFileSystem,
                                 projectVirtualFileSystem: ProjectVirtualFileSystem,
                                 clientLogger: ClientLogger,
                                 languageClient: WeaveLanguageClient,
                                 validationService: ValidationService
                               ) extends WorkspaceService {

  private val logger: Logger = Logger.getLogger(getClass.getName)
  private val commandProvider: CommandProvider = new CommandProvider(vfs, projectVirtualFileSystem, clientLogger, languageClient, project, projectKind, validationService)

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    logger.log(Level.INFO, "didChangeConfiguration: " + params.getSettings)
    project.settings.update(params.getSettings)
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
      clientLogger.logInfo("[DataWeaveWorkspaceService] Changed Watched File : " + fe.getUri + " - " + fe.getType)
      eventBus.fire(new FileChangedEvent(fe.getUri, fe.getType))
    })
  }
}
