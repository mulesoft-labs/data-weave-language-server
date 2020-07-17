package org.mule.weave.lsp

import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.services.LSPWeaveToolingService

class DataWeaveWorkspaceService(weaveService: LSPWeaveToolingService) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    println("[DataWeaveWorkspaceService] Changed Configuration: " + params.getSettings)
  }

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    println("[DataWeaveWorkspaceService] executeCommand: " + params)
    throw new UnsupportedOperationException
  }

  override def didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams): Unit = {
    println("[DataWeaveWorkspaceService] Changed Folders: " + params.getEvent)
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    params.getChanges.forEach((fe) => {
      println("[DataWeaveWorkspaceService] Changed Watched File : " + fe.getUri + " - " + fe.getType)
      fe.getType match {
        case FileChangeType.Created => {
          weaveService.vfs.created(fe.getUri)
        }
        case FileChangeType.Changed => {
          weaveService.vfs.changed(fe.getUri)
        }
        case FileChangeType.Deleted => {
          weaveService.vfs.deleted(fe.getUri)
        }
      }
    })
  }
}
