package org.mule.weave.lsp

import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.indexer.LSPWeaveIndexService
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.commands.ProjectProvider
import org.mule.weave.lsp.project.events.ProjectInitializedEvent
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveDocumentService
import org.mule.weave.lsp.services.DataWeaveWorkspaceService
import org.mule.weave.lsp.services.TextDocumentServiceDelegate
import org.mule.weave.lsp.services.ValidationService
import org.mule.weave.lsp.services.WorkspaceServiceDelegate
import org.mule.weave.lsp.utils.DataWeaveDirectoryUtils
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.completion.EmptyDataFormatDescriptorProvider
import org.mule.weave.v2.deps.MavenDependencyAnnotationProcessor
import org.mule.weave.v2.deps.ResourceDependencyAnnotationProcessor
import org.mule.weave.v2.editor.CompositeFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.editor.WeaveToolingService

import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

class WeaveLanguageServer extends LanguageServer {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  private val eventbus = new EventBus(IDEExecutors.eventsExecutor())
  private val executorService = IDEExecutors.defaultExecutor()

  private val weaveWorkspaceService: WorkspaceServiceDelegate = new WorkspaceServiceDelegate()
  private var globalFVS: VirtualFileSystem = _

  private val textDocumentService: TextDocumentServiceDelegate = new TextDocumentServiceDelegate()

  private var client: WeaveLanguageClient = _
  private var workspaceUri: URI = _

  private var clientLogger: ClientLogger = _
  private var indexService: LSPWeaveIndexService = _
  private var projectValue: Project = _

  private def createWeaveToolingService(): WeaveToolingService = {
    val artifactResolutionCallback = MavenDependencyManagerUtils.callback(eventbus, (_, _) => {})
    val resourceDependencyAnnotationProcessor = ResourceDependencyAnnotationProcessor(
      new File(DataWeaveDirectoryUtils.getCacheHome(), "resources"),
      artifactResolutionCallback,
      executorService
    )
    val mavenDependencyAnnotationProcessor = new MavenDependencyAnnotationProcessor(artifactResolutionCallback, MavenDependencyManagerUtils.MAVEN)
    //    val moduleLoader = new RamlModuleLoader()
    //    moduleLoader.resolver(globalFVS.asResourceResolver)
    //    val toolingService = new WeaveToolingService(globalFVS, EmptyDataFormatDescriptorProvider, Array(DefaultModuleLoaderFactory()))
    val toolingService = new WeaveToolingService(globalFVS, EmptyDataFormatDescriptorProvider)
    toolingService.registerParsingAnnotationProcessor(ResourceDependencyAnnotationProcessor.ANNOTATION_NAME, resourceDependencyAnnotationProcessor)
    toolingService.registerParsingAnnotationProcessor(MavenDependencyAnnotationProcessor.ANNOTATION_NAME, mavenDependencyAnnotationProcessor)

    toolingService.withSymbolsIndexService(indexService)
    toolingService
  }

  def eventBus(): EventBus = eventbus

  def project(): Project = projectValue

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    logger.log(Level.INFO, s"initialize(${params})")
    workspaceUri = new URI(params.getRootUri)
    clientLogger = new ClientLogger(client)
    clientLogger.logInfo("[DataWeave] Root URI: " + workspaceUri)
    clientLogger.logInfo("[DataWeave] Initialization Option: " + params.getInitializationOptions)
    projectValue = Project.create(params, eventbus)
    val projectKind: ProjectKind = ProjectKindDetector.detectProjectKind(projectValue, eventbus, clientLogger)
    clientLogger.logInfo("[DataWeave] Detected Project: " + projectKind.name())

    clientLogger.logInfo("[DataWeave] Project: " + projectKind.name() + " initialized ok.")
    val librariesVFS: LibrariesVirtualFileSystem = new LibrariesVirtualFileSystem(eventbus, clientLogger)
    val projectVFS: ProjectVirtualFileSystem = new ProjectVirtualFileSystem(eventbus, projectKind.structure())

    indexService = new LSPWeaveIndexService(eventbus, clientLogger, client, projectVFS, projectKind)
    globalFVS = new CompositeFileSystem(projectVFS, librariesVFS)

    val validationServices = new ValidationService(projectValue, eventbus, client, globalFVS, createWeaveToolingService, executorService)

    textDocumentService.delegate = new DataWeaveDocumentService(validationServices, executorService, projectVFS, projectKind, globalFVS)
    weaveWorkspaceService.delegate = new DataWeaveWorkspaceService(projectValue, projectKind, eventbus, globalFVS, projectVFS, clientLogger, client, validationServices)

    initializeProject(projectValue, projectKind)

    val capabilities = new ServerCapabilities
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


  private def initializeProject(project: Project, projectKind: ProjectKind) = {
    executorService.submit(new Runnable {
      override def run(): Unit = {
        try {
          clientLogger.logInfo(s"[DataWeave] Project Kind: ${projectKind.name()} Initializing.")
          projectKind.setup()
          val dependencyManager = projectKind.dependencyManager()
          clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " initializing.")
          dependencyManager.init()
          clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " initialized.")
          clientLogger.logInfo("[DataWeave] Indexing initialized.")
          indexService.init()
          project.markInitialized
          eventbus.fire(new ProjectInitializedEvent(project))
        } catch {
          case e: Exception => {
            clientLogger.logError("Unable to initialize project.", e)
            ///
          }
        }
      }
    })
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

  def connect(client: WeaveLanguageClient): Unit = {
    logger.log(Level.INFO, "connect")
    this.client = client
  }

  @JsonNotification("weave/project/create")
  def createProject(): Unit = {
    val projectProvider = new ProjectProvider(client, workspaceUri)
    projectProvider.newProject()
  }
}
