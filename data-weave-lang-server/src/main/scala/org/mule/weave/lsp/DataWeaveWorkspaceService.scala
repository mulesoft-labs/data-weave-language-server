package org.mule.weave.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.services.WeaveLSPService

class DataWeaveWorkspaceService(weaveService: WeaveLSPService) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {

  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    params.getChanges.forEach((fe) => {

      fe.getType match {
        case FileChangeType.Created => {
          weaveService.vfs.created(fe.getUri)
        }
        case FileChangeType.Changed =>{
          weaveService.vfs.changed(fe.getUri)
        }
        case FileChangeType.Deleted => {
          weaveService.vfs.deleted(fe.getUri)
        }
      }
    })
  }
}
