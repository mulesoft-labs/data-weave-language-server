package org.mule.weave.lsp.services

import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem

class DataWeaveWorkspaceService(projectVFS: ProjectVirtualFileSystem, pd:ProjectDefinition, dwTooling: LSPWeaveToolingService) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    dwTooling.logInfo("[DataWeaveWorkspaceService] Changed Configuration: " + params.getSettings)
    pd.updateSettings(params)
  }

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    dwTooling.logInfo("[DataWeaveWorkspaceService] executeCommand: " + params)

    CompletableFuture.completedFuture(null)
  }

  override def didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams): Unit = {
    println("[DataWeaveWorkspaceService] Changed Folders: " + params.getEvent)
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    params.getChanges.forEach((fe) => {
      dwTooling.logInfo("[DataWeaveWorkspaceService] Changed Watched File : " + fe.getUri + " - " + fe.getType)
      fe.getType match {
        case FileChangeType.Created => {
          projectVFS.created(fe.getUri)
        }
        case FileChangeType.Changed => {
          projectVFS.changed(fe.getUri)
        }
        case FileChangeType.Deleted => {
          projectVFS.deleted(fe.getUri)
        }
      }
    })
  }
}
