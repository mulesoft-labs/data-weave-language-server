package org.mule.weave.lsp.services.delegate

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.WorkspaceService

import java.util
import java.util.concurrent.CompletableFuture

class WorkspaceServiceDelegate extends WorkspaceService {

  var delegate: WorkspaceService = _

  override def executeCommand(params: ExecuteCommandParams): CompletableFuture[AnyRef] = {
    delegate.executeCommand(params)
  }

  override def symbol(params: WorkspaceSymbolParams): CompletableFuture[util.List[_ <: SymbolInformation]] = {
    delegate.symbol(params)
  }

  override def didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams): Unit = {
    if (delegate != null) {
      delegate.didChangeWorkspaceFolders(params)
    }
  }

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    if (delegate != null) {
      delegate.didChangeConfiguration(params)
    }
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    if (delegate != null) {
      delegate.didChangeWatchedFiles(params)
    }
  }
}
