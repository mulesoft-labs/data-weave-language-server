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
import org.mule.weave.lsp.services.ProjectDefinitionServiceFactory
import org.mule.weave.lsp.services.ProjectDefinitionServiceProxy
import org.mule.weave.lsp.services.WeaveLSPService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.completion.EmptyDataFormatDescriptorProvider
import org.mule.weave.v2.editor.WeaveToolingService

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {


  private val executorService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-%d\"")
      .setDaemon(true)
      .build()
  )

  private val projectDefinitionService: ProjectDefinitionServiceProxy = new ProjectDefinitionServiceProxy()
  private val projectVirtualFileSystem: ProjectVirtualFileSystem = new ProjectVirtualFileSystem(projectDefinitionService)
  private val weaveService = new WeaveLSPService(createWeaveToolingService, executorService, projectVirtualFileSystem)
  private val textDocumentService = new DataWeaveDocumentService(weaveService, executorService)

  private def createWeaveToolingService(): WeaveToolingService = {
    new WeaveToolingService(projectVirtualFileSystem, EmptyDataFormatDescriptorProvider, Array())
  }

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    println("[DataWeave] Root URI: " + params.getRootUri)
    println("[DataWeave] Initialization Option: " + params.getInitializationOptions)

    val factory = new ProjectDefinitionServiceFactory()
    projectDefinitionService.proxy = factory.createProjectDefinition(params)

    val capabilities = new ServerCapabilities
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
    textDocumentService
  }

  override def getWorkspaceService: WorkspaceService = {
    new DataWeaveWorkspaceService(weaveService)
  }

  override def connect(client: LanguageClient): Unit = {
    println("[DataWeave] Connect ")
    this.weaveService.connect(client)
  }
}