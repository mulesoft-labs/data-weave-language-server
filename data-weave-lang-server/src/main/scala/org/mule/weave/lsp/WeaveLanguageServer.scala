package org.mule.weave.lsp

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder
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
import org.mule.weave.lsp.vfs.ClassloaderVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.CompositeFileSystem

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {

  var params: InitializeParams = _
  var client: LanguageClient = _

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    println("[DataWeave] Root URI: " + params.getRootUri)
    println("[DataWeave] Capabilities: " + params.getCapabilities)
    println("[DataWeave] Initialization Option: " + params.getInitializationOptions)

    val capabilities = new ServerCapabilities
    this.params = params
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setCompletionProvider(new CompletionOptions(true, java.util.Arrays.asList(".", ",")))
    capabilities.setHoverProvider(true)
    capabilities.setDocumentSymbolProvider(true)
    capabilities.setDefinitionProvider(true)
    capabilities.setRenameProvider(true)
    capabilities.setReferencesProvider(true)
    CompletableFuture.completedFuture(new InitializeResult(capabilities))
  }

  override def shutdown(): CompletableFuture[AnyRef] = {
    println("[DataWeave] ShutDown")
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = {
    println("[DataWeave] Exit")
    System.exit(0)
  }

  override def getTextDocumentService: TextDocumentService = {
    val executorService = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder()
        .setNameFormat("dw-lang-server-%d\"")
        .setDaemon(true)
        .build()
    )

    val classloaderVirtualFileSystem = new ClassloaderVirtualFileSystem(getClass.getClassLoader)
    val projectClassLoader = new CompositeFileSystem(new ProjectVirtualFileSystem(this), classloaderVirtualFileSystem)
    new DataWeaveDocumentService(projectClassLoader, this.client, executorService)
  }

  override def getWorkspaceService: WorkspaceService = {
    new DataWeaveWorkspaceService(this.params)
  }

  override def connect(client: LanguageClient): Unit = {
    println("[DataWeave] Connect " + client)
    this.client = client
  }
}