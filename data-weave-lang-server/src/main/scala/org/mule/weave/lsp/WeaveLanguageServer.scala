package org.mule.weave.lsp

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
import org.mule.weave.lsp.bat.BatProjectManager
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.services.DataWeaveDocumentService
import org.mule.weave.lsp.services.DataWeaveWorkspaceService
import org.mule.weave.lsp.services.LSPToolingServices
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

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {


  private val executorService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-%d\"")
      .setDaemon(true)
      .build()
  )

  private val logger: MessageLoggerService = new MessageLoggerService

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

  private val batProjectManager: BatProjectManager = new BatProjectManager(dependencyManager, logger)

  private val projectDefinition = new ProjectDefinition(librariesVFS, batProjectManager)

  private var weaveWorkspaceService: DataWeaveWorkspaceService = _

  private val projectVFS: ProjectVirtualFileSystem = new ProjectVirtualFileSystem(projectDefinition)

  private val virtualFileSystems: Seq[VirtualFileSystem] = Seq(projectVFS, librariesVFS, new ClassloaderVirtualFileSystem(this.getClass.getClassLoader))

  private val globalFVS = new ChainedVirtualFileSystem(virtualFileSystems)

  private val dwTooling: LSPToolingServices = new LSPToolingServices(createWeaveToolingService, executorService, projectVFS, projectDefinition, librariesVFS)

  private val textDocumentService: DataWeaveDocumentService = new DataWeaveDocumentService(dwTooling, executorService, projectVFS, globalFVS)

  private def createWeaveToolingService(): WeaveToolingService = {

    val moduleLoader = new RamlModuleLoader()
    moduleLoader.resolver(globalFVS.asResourceResolver)
    val toolingService = new WeaveToolingService(globalFVS, EmptyDataFormatDescriptorProvider, Array(DefaultModuleLoaderFactory(moduleLoader)))
    toolingService.registerParsingAnnotationProcessor(ResourceDependencyAnnotationProcessor.ANNOTATION_NAME, resourceDependencyAnnotationProcessor)
    toolingService.registerParsingAnnotationProcessor(MavenDependencyAnnotationProcessor.ANNOTATION_NAME, mavenDependencyAnnotationProcessor)
    toolingService
  }

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    logger.logInfo("[DataWeave] Root URI: " + params.getRootUri)
    logger.logInfo("[DataWeave] Initialization Option: " + params.getInitializationOptions)
    this.projectDefinition.initialize(params)
    val capabilities = new ServerCapabilities
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions())
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setCompletionProvider(new CompletionOptions(true, java.util.Arrays.asList(".", ",")))
    capabilities.setHoverProvider(true)
    capabilities.setDocumentSymbolProvider(true)
    capabilities.setDefinitionProvider(true)
    capabilities.setDocumentFormattingProvider(true)
    capabilities.setFoldingRangeProvider(true)
    capabilities.setCodeActionProvider(true)
    capabilities.setRenameProvider(true)
    capabilities.setReferencesProvider(true)
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Commands.ALL_COMMANDS))
    CompletableFuture.completedFuture(new InitializeResult(capabilities))
  }

  override def shutdown(): CompletableFuture[AnyRef] = {
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = {
    System.exit(0)
  }

  override def getTextDocumentService: TextDocumentService = {
    textDocumentService
  }

  override def getWorkspaceService: WorkspaceService = {
    if (weaveWorkspaceService == null) {
      weaveWorkspaceService = new DataWeaveWorkspaceService(projectVFS, projectDefinition, this.logger, dwTooling)
    }
    weaveWorkspaceService
  }

  override def connect(client: LanguageClient): Unit = {
    this.dwTooling.connect(client)
    this.projectDefinition.connect(client)
    this.logger.connect(client)
  }

}
