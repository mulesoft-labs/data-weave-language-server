package org.mule.weave.lsp

import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveDocumentService
import org.mule.weave.lsp.services.DataWeaveWorkspaceService
import org.mule.weave.lsp.services.TextDocumentServiceDelegate
import org.mule.weave.lsp.services.ValidationServices
import org.mule.weave.lsp.services.WorkspaceServiceDelegate
import org.mule.weave.lsp.utils.DataWeaveUtils
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.completion.EmptyDataFormatDescriptorProvider
import org.mule.weave.v2.deps.MavenDependencyAnnotationProcessor
import org.mule.weave.v2.deps.ResourceDependencyAnnotationProcessor
import org.mule.weave.v2.editor.CompositeFileSystem
import org.mule.weave.v2.editor.DefaultModuleLoaderFactory
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.module.raml.RamlModuleLoader

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

class WeaveLanguageServer extends LanguageServer with LanguageClientAware {

  val logger = Logger.getLogger(getClass.getName)

  private val eventBus = new EventBus(IDEExecutors.eventsExecutor())
  private val executorService = IDEExecutors.defaultExecutor()

  private val weaveWorkspaceService: WorkspaceServiceDelegate = new WorkspaceServiceDelegate()
  private var globalFVS: VirtualFileSystem = _

  private val textDocumentService: TextDocumentServiceDelegate = new TextDocumentServiceDelegate()
  private var client: LanguageClient = _

  private def createWeaveToolingService(): WeaveToolingService = {
    val artifactResolutionCallback = MavenDependencyManagerUtils.callback(eventBus)
    val resourceDependencyAnnotationProcessor = ResourceDependencyAnnotationProcessor(
      new File(DataWeaveUtils.getCacheHome(), "resources"),
      artifactResolutionCallback,
      executorService
    )
    val mavenDependencyAnnotationProcessor = new MavenDependencyAnnotationProcessor(artifactResolutionCallback, MavenDependencyManagerUtils.MAVEN)
    val moduleLoader = new RamlModuleLoader()
    moduleLoader.resolver(globalFVS.asResourceResolver)
    val toolingService = new WeaveToolingService(globalFVS, EmptyDataFormatDescriptorProvider, Array(DefaultModuleLoaderFactory(moduleLoader)))
    toolingService.registerParsingAnnotationProcessor(ResourceDependencyAnnotationProcessor.ANNOTATION_NAME, resourceDependencyAnnotationProcessor)
    toolingService.registerParsingAnnotationProcessor(MavenDependencyAnnotationProcessor.ANNOTATION_NAME, mavenDependencyAnnotationProcessor)
    toolingService
  }

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    logger.log(Level.INFO, s"initialize(${params})")
    val clientLogger = new ClientLogger(client)

    clientLogger.logInfo("[DataWeave] Root URI: " + params.getRootUri)
    clientLogger.logInfo("[DataWeave] Initialization Option: " + params.getInitializationOptions)
    val project: Project = Project.create(params, eventBus)
    val projectKind: ProjectKind = ProjectKindDetector.detectProjectKind(project, eventBus, clientLogger)
    clientLogger.logInfo("[DataWeave] Detected Project: " + projectKind.name())

    clientLogger.logInfo("[DataWeave] Project: " + projectKind.name() + " initialized ok.")
    val librariesVFS: LibrariesVirtualFileSystem = new LibrariesVirtualFileSystem(eventBus, clientLogger)
    val projectVFS: ProjectVirtualFileSystem = new ProjectVirtualFileSystem(eventBus, projectKind.structure())
    globalFVS = new CompositeFileSystem(projectVFS, librariesVFS)

    val dwTooling = new ValidationServices(project, eventBus, client, globalFVS, createWeaveToolingService, executorService)
    textDocumentService.delegate = new DataWeaveDocumentService(dwTooling, executorService, projectVFS, globalFVS)
    weaveWorkspaceService.delegate = new DataWeaveWorkspaceService(project, eventBus, globalFVS, clientLogger, dwTooling)

    initializeProject(clientLogger, projectKind)

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


  private def initializeProject(clientLogger: ClientLogger, projectKind: ProjectKind) = {
    try {
      projectKind.init()
      val dependencyManager = projectKind.dependencyManager()
      clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " initializing.")
      dependencyManager.init()
      clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " initialized.")
    } catch {
      case e: Exception => {
        clientLogger.logError("Unable to initialize project.", e)
        ///
      }
    }
  }

  override def initialized(params: InitializedParams): Unit = {}

  override def shutdown(): CompletableFuture[AnyRef] = {
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = {
    System.exit(0)
  }

  override def getTextDocumentService: TextDocumentService = {
    logger.log(Level.INFO, "getTextDocumentService")
    textDocumentService
  }

  override def getWorkspaceService: WorkspaceService = {
    logger.log(Level.INFO, "getWorkspaceService")
    weaveWorkspaceService
  }

  override def connect(client: LanguageClient): Unit = {
    logger.log(Level.INFO, "connect")
    this.client = client
  }

}
