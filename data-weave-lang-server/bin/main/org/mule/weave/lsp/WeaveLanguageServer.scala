package org.mule.weave.lsp

import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {

  var params: InitializeParams = _
  var client: LanguageClient = _

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    val capabilities = new ServerCapabilities
    this.params = params
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setCompletionProvider(new CompletionOptions(true, java.util.Arrays.asList(".", ",")))
    val result = new InitializeResult(capabilities)
   null
  }

  override def shutdown(): CompletableFuture[AnyRef] = {
   null
  }

  override def exit(): Unit = {
    System.exit(0)
  }

  override def getTextDocumentService: TextDocumentService = {
    new DataWeaveDocumentService(this.params)
  }

  override def getWorkspaceService: WorkspaceService = {
    new DataWeaveWorkspaceService(this.params)
  }

  override def connect(client: LanguageClient): Unit = {
    this.client = client
  }
}