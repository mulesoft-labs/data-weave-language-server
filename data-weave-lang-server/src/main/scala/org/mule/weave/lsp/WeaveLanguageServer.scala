package org.mule.weave.lsp

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder
import coursier.cache.CacheLogger
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.services.DataWeaveDocumentService
import org.mule.weave.lsp.services.DataWeaveWorkspaceService
import org.mule.weave.lsp.services.LSPWeaveToolingService
import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.lsp.services.ProjectDefinition
import org.mule.weave.lsp.utils.DataWeaveUtils
import org.mule.weave.lsp.vfs.ChainedVirtualFileSystem
import org.mule.weave.lsp.vfs.ClassloaderVirtualFileSystem
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.completion.EmptyDataFormatDescriptorProvider
import org.mule.weave.v2.deps.MavenDependencyAnnotationProcessor
import org.mule.weave.v2.deps.MavenDependencyManager
import org.mule.weave.v2.deps.ResourceDependencyAnnotationProcessor
import org.mule.weave.v2.editor.DefaultModuleLoaderFactory
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.module.raml.RamlModuleLoader

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {


  private val executorService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-%d\"")
      .setDaemon(true)
      .build()
  )

  private val logger = new MessageLoggerService

  private val dependencyManager = new MavenDependencyManager(new File(DataWeaveUtils.getCacheHome(), "maven"),
    executorService,
    new CacheLogger {
      override def downloadedArtifact(url: String, success: Boolean): Unit = {
        if (success)
          logger.logInfo(s"Downloaded: ${url}")
      }
    }
  )

  private val librariesVFS = new LibrariesVirtualFileSystem(dependencyManager, logger)

  private val resourceDependencyAnnotationProcessor = ResourceDependencyAnnotationProcessor(
    new File(DataWeaveUtils.getCacheHome(), "resources"),
    librariesVFS,
    executorService
  )

  private val mavenDependencyAnnotationProcessor = new MavenDependencyAnnotationProcessor(librariesVFS, dependencyManager)

  private val projectDefinition = new ProjectDefinition(librariesVFS)

  private val projectVFS: ProjectVirtualFileSystem = new ProjectVirtualFileSystem(projectDefinition)

  private val dwTooling: LSPWeaveToolingService = new LSPWeaveToolingService(createWeaveToolingService, executorService, projectVFS, projectDefinition, librariesVFS)

  private val textDocumentService: DataWeaveDocumentService = new DataWeaveDocumentService(dwTooling, executorService, projectVFS)

  private def createWeaveToolingService(): WeaveToolingService = {
    //TODO: Remove when possible the classloader and put everything inside libraries
    val virtualFileSystems: Seq[VirtualFileSystem] = Seq(projectVFS, librariesVFS, new ClassloaderVirtualFileSystem(this.getClass.getClassLoader))
    val globalFVS = new ChainedVirtualFileSystem(virtualFileSystems)
    val moduleLoader = new RamlModuleLoader()
    moduleLoader.resolver(globalFVS.asResourceResolver)
    val toolingService = new WeaveToolingService(globalFVS, EmptyDataFormatDescriptorProvider, Array(DefaultModuleLoaderFactory(moduleLoader)))
    toolingService.registerParsingAnnotationProcessor(ResourceDependencyAnnotationProcessor.ANNOTATION_NAME, resourceDependencyAnnotationProcessor)
    toolingService.registerParsingAnnotationProcessor(MavenDependencyAnnotationProcessor.ANNOTATION_NAME, mavenDependencyAnnotationProcessor)
    toolingService
  }

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    println("[DataWeave] Root URI: " + params.getRootUri)
    println("[DataWeave] Initialization Option: " + params.getInitializationOptions)
    this.projectDefinition.initialize(params)
    val capabilities = new ServerCapabilities
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions())
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setCompletionProvider(new CompletionOptions(true, java.util.Arrays.asList(".", ",")))
    capabilities.setHoverProvider(true)
    capabilities.setDocumentSymbolProvider(true)
    capabilities.setDefinitionProvider(true)
    capabilities.setDocumentFormattingProvider(true)
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
    new DataWeaveWorkspaceService(projectVFS, projectDefinition)
  }

  override def connect(client: LanguageClient): Unit = {
    println("[DataWeave] Connect ")
    this.dwTooling.connect(client)
    this.projectDefinition.connect(client)
    this.logger.connect(client)
  }


}