package org.mule.weave.lsp

import org.eclipse.lsp4j.CodeLensOptions
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
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.project.service.ToolingService
import org.mule.weave.lsp.project.service.WeaveAgentService
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveDocumentService
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.services.DataWeaveWorkspaceService
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.lsp.services.TextDocumentServiceDelegate
import org.mule.weave.lsp.services.WorkspaceServiceDelegate
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
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
import scala.collection.mutable.ArrayBuffer

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

  private val services: ArrayBuffer[ToolingService] = ArrayBuffer()

  private def createWeaveToolingService(): WeaveToolingService = {
    val artifactResolutionCallback = MavenDependencyManagerUtils.callback(eventbus, (_, _) => {})
    val resourceDependencyAnnotationProcessor = ResourceDependencyAnnotationProcessor(
      new File(WeaveDirectoryUtils.getCacheHome(), "resources"),
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

    //Create FileSystem
    val librariesVFS: LibrariesVirtualFileSystem = new LibrariesVirtualFileSystem(clientLogger)
    val projectVFS: ProjectVirtualFileSystem = new ProjectVirtualFileSystem()
    globalFVS = new CompositeFileSystem(projectVFS, librariesVFS)

    //Create Services
    val dataWeaveToolingService = new DataWeaveToolingService(projectValue, client, globalFVS, createWeaveToolingService, executorService)
    val weaveAgentService = new WeaveAgentService(dataWeaveToolingService, IDEExecutors.defaultExecutor(), clientLogger, projectValue)
    val previewService = new PreviewService(weaveAgentService, client, projectValue)
    indexService = new LSPWeaveIndexService(clientLogger, client, projectVFS)

    //Create the project
    val projectKind: ProjectKind = ProjectKindDetector.detectProjectKind(projectValue, eventbus, clientLogger, weaveAgentService)
    clientLogger.logInfo("[DataWeave] Detected Project: " + projectKind.name())
    clientLogger.logInfo("[DataWeave] Project: " + projectKind.name() + " initialized ok.")

    //Init The LSP Services
    val documentService = new DataWeaveDocumentService(dataWeaveToolingService, executorService, projectVFS, globalFVS)
    textDocumentService.delegate = documentService
    val workspaceService = new DataWeaveWorkspaceService(projectValue, globalFVS, projectVFS, clientLogger, client, dataWeaveToolingService, previewService)
    weaveWorkspaceService.delegate = workspaceService



    services.++=(Seq(
      workspaceService,
      documentService,
      weaveAgentService,
      dataWeaveToolingService,
      previewService,
      indexService,
      projectVFS,
      librariesVFS
    ))

    //Init the Services
    services.foreach((service) => {
      service.init(projectKind, eventbus)
    })

    //Start the project
    executorService.submit(new Runnable {
      override def run(): Unit = {
        try {
          clientLogger.logInfo(s"[DataWeave] Project Kind: ${projectKind.name()} Starting.")
          projectKind.start()
          val dependencyManager: ProjectDependencyManager = projectKind.dependencyManager()
          clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " Starting.")
          dependencyManager.start()
          clientLogger.logInfo("[DataWeave] Dependency Manager: " + dependencyManager.getClass + " Started.")
          clientLogger.logInfo("[DataWeave] Starting other services.")
          //Start the Services
          services.foreach((service) => {
            service.start()
          })
          //Mark project as initialized
          projectValue.markStarted
          eventbus.fire(new ProjectStartedEvent(projectValue))
        } catch {
          case e: Exception => {
            clientLogger.logError("Unable to Start project.", e)
            ///
          }
        }
      }
    })


    val capabilities = new ServerCapabilities
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setCompletionProvider(new CompletionOptions(true, java.util.Arrays.asList(".", ",")))
    capabilities.setHoverProvider(true)
    capabilities.setDocumentSymbolProvider(true)
    capabilities.setDefinitionProvider(true)
    capabilities.setDocumentFormattingProvider(true)
    capabilities.setFoldingRangeProvider(true)
    capabilities.setCodeActionProvider(true)
    capabilities.setCodeLensProvider(new CodeLensOptions(true))
    capabilities.setRenameProvider(true)
    capabilities.setReferencesProvider(true)
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Commands.ALL_COMMANDS))
    CompletableFuture.completedFuture(new InitializeResult(capabilities))
  }


  override def initialized(params: InitializedParams): Unit = {}

  override def shutdown(): CompletableFuture[AnyRef] = {
    CompletableFuture.supplyAsync(() => {
      logger.log(Level.INFO, "Stopping the services")
      services.foreach(_.stop())
      null
    })
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
