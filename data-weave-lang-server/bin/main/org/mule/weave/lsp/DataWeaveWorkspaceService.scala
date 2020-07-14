package org.mule.weave.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.WorkspaceService

class DataWeaveWorkspaceService(params: InitializeParams) extends WorkspaceService{
  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {

  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {

  }
}
