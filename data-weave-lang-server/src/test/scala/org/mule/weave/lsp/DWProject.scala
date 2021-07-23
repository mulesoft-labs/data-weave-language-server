package org.mule.weave.lsp

import com.google.gson.JsonPrimitive
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DeleteFile
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages
import org.mule.weave.lsp.extension.client.DependenciesParams
import org.mule.weave.lsp.extension.client.JobEndedParams
import org.mule.weave.lsp.extension.client.JobStartedParams
import org.mule.weave.lsp.extension.client.LaunchConfiguration
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.OpenWindowsParams
import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.extension.client.ShowScenariosParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.extension.client.WeaveQuickPickResult
import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent
import org.mule.weave.lsp.indexer.events.OnIndexingFinished
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.utils.URLUtils

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors
import scala.collection.concurrent.TrieMap
import scala.io.Codec
import scala.io.Source

class DWProject(val workspaceRoot: Path) {

  var clientUI: ClientUI = DefaultInputsInteraction
  private val logger: Logger = Logger.getLogger("[" + workspaceRoot.toFile.getName + "]")

  private var lspValue: WeaveLanguageServer = _

  private val diagnosticsValue: TrieMap[String, PublishDiagnosticsParams] = TrieMap()

  private val lock = new Object

  def open(relativePath: String): DWProject = {
    val filePath = toAbsolutePath(relativePath)
    val item: TextDocumentItem = new TextDocumentItem(filePath.toUri.toString, "DataWeave", 2, toString(filePath))
    val openTextDocumentParams = new DidOpenTextDocumentParams(item)
    lsp().getTextDocumentService.didOpen(openTextDocumentParams)
    this
  }

  def waitForProjectInitialized(): Unit = {
    if (!lsp().project().isStarted()) {
      val latch = new CountDownLatch(1)
      lsp().eventBus().register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
        override def onProjectStarted(project: Project): Unit = {
          latch.countDown()
        }
      })
      latch.await(10, TimeUnit.MINUTES)
    }
  }

  def withClientUI(clientUI: ClientUI): DWProject = {
    this.clientUI = clientUI
    this
  }

  def runCommand(commandId: String, args: String*): AnyRef = {
    val argsArray: Array[JsonPrimitive] = args.map((arg) => new JsonPrimitive(arg)).toArray
    lsp().getWorkspaceService
      .executeCommand(new ExecuteCommandParams(commandId, util.Arrays.asList(argsArray: _*)))
      .get()
  }

  def waitForProjectIndexed(): Unit = {
    val latch = new CountDownLatch(1)
    lsp().eventBus().register(IndexingFinishedEvent.INDEXING_FINISHED, new OnIndexingFinished {
      override def onIndexingFinished(): Unit = {
        latch.countDown()
      }
    })
    latch.await(10, TimeUnit.MINUTES)
  }

  def rename(relativePath: String, line: Int, column: Int, newName: String): WorkspaceEdit = {
    open(relativePath)
    val absolutePath: Path = toAbsolutePath(relativePath)
    val position = new Position(line, column)
    val identifier = new TextDocumentIdentifier(absolutePath.toUri.toString)
    val value = lsp().getTextDocumentService.rename(new RenameParams(identifier, position, newName))
    value.get(10, TimeUnit.MINUTES)
  }

  def codeLenses(relativePath: String): util.List[_ <: CodeLens] = {
    open(relativePath)
    val absolutePath = toAbsolutePath(relativePath)
    val value = lsp().getTextDocumentService.codeLens(new CodeLensParams(new TextDocumentIdentifier(absolutePath.toUri.toString)))
    value.get(10, TimeUnit.MINUTES)
  }

  def referencesOfLocalFile(relativePath: String, line: Int, column: Int): util.List[_ <: Location] = {
    open(relativePath)
    val filePath: Path = toAbsolutePath(relativePath)
    val absolutPath = filePath.toUri.toString
    referencesOf(absolutPath, line, column)
  }


  def referencesOf(absolutPath: String, line: Int, column: Int): util.List[_ <: Location] = {
    val referenceContext = new ReferenceContext(true)
    val position = new Position(line, column)
    val identifier = new TextDocumentIdentifier(absolutPath)
    val value = lsp().getTextDocumentService.references(new ReferenceParams(identifier, position, referenceContext))
    value.get(10, TimeUnit.MINUTES)
  }

  def definition(relativePath: String, line: Int, column: Int): util.List[_ <: LocationLink] = {
    open(relativePath)
    val filePath: Path = toAbsolutePath(relativePath)
    val position = new Position(line, column)
    val identifier = new TextDocumentIdentifier(filePath.toUri.toString)
    val value = lsp().getTextDocumentService.definition(new DefinitionParams(identifier, position))
    value.get(10, TimeUnit.MINUTES).getRight
  }

  def update(relativePath: String, content: String): Unit = {
    cleanDiagnostics(relativePath)
    val filePath = toAbsolutePath(relativePath)
    val didChangeTextDocumentParams = new DidChangeTextDocumentParams()
    val documentIdentifier = new VersionedTextDocumentIdentifier()
    documentIdentifier.setUri(filePath.toUri.toString)
    didChangeTextDocumentParams.setContentChanges(util.Arrays.asList(new TextDocumentContentChangeEvent(content)))
    didChangeTextDocumentParams.setTextDocument(documentIdentifier)
    lsp().getTextDocumentService.didChange(didChangeTextDocumentParams)
  }

  def cleanDiagnostics(): Unit = {
    println("[DWProject] Clear Diagnostics")
    diagnosticsValue.clear()
  }

  def cleanDiagnostics(relativePath: String): Unit = {
    val uri = toAbsolutePath(relativePath).toUri.toString
    val maybeParams = diagnosticsValue.remove(uri)
    println(s"[DWProject] cleanDiagnostics ${uri} \n${maybeParams}")
  }

  private def toAbsolutePath(relativePath: String) = {
    workspaceRoot.resolve(relativePath).toAbsolutePath.normalize()
  }

  private def lsp() = {
    if (lspValue == null) {
      init((d) => {
        println("[DWProject] Diagnostics for: `" + d.getUri + "`\n" + d)
        diagnosticsValue.put(d.getUri, d)
        lock.synchronized({
          lock.notify()
        })
      })
    }
    lspValue
  }

  def diagnostics(): Seq[PublishDiagnosticsParams] = {
    diagnosticsValue.values.toSeq
  }

  def diagnosticsFor(path: String): Option[PublishDiagnosticsParams] = {
    val absolutePath = toAbsolutePath(path).toUri.toString
    while (!diagnosticsValue.contains(absolutePath)) {
      println(s"[DWProject] No diagnosticsFor: `${absolutePath}` waiting for validation.")
      lock.synchronized({
        lock.wait(TimeUnit.MINUTES.toNanos(10))
      })
    }
    val maybeParams = diagnosticsValue.get(absolutePath)
    println(s"[DWProject] Found diagnosticsFor: `${absolutePath}`\n" + maybeParams.get)
    maybeParams
  }

  def errorsFor(path: String): util.List[Diagnostic] = {
    diagnosticsFor(path)
      .getOrElse(throw new RuntimeException(s"No diagnostics for ${path}."))
      .getDiagnostics.stream()
      .filter((d) => {
        d.getSeverity == DiagnosticSeverity.Error
      }).collect(Collectors.toList[Diagnostic]())
  }

  private def toString(filePath: Path) = {
    val source = Source.fromURI(filePath.toUri)(Codec.UTF8)
    try {
      source.mkString
    } finally {
      source.close()
    }
  }


  def toOffset(text: String, line: Int, column: Int): Int = {
    val iterator = text.linesIterator
    var index = 0
    var cLine = -1
    while (iterator.hasNext && line != cLine) {
      val lineText = iterator.next()
      index = lineText.length + 1
      cLine = cLine + 1
      if (line == cLine) {
        index = index + column
      }
    }
    index
  }

  def init(diagnosticsListener: (PublishDiagnosticsParams) => Unit): WeaveLanguageServer = {
    init(new WeaveLanguageClient {
      override def telemetryEvent(`object`: Any): Unit = {
      }


      override def applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture[ApplyWorkspaceEditResponse] = {
        val edit = params.getEdit
        val changes: util.List[messages.Either[TextDocumentEdit, ResourceOperation]] = edit.getDocumentChanges
        changes.stream().forEach((change) => {
          if (change.isLeft) {
            val left: TextDocumentEdit = change.getLeft
            val maybePath = URLUtils.toPath(left.getTextDocument.getUri)
            maybePath match {
              case Some(thePath) => {
                val file = thePath.toFile
                val content = if (file.exists()) {
                  val source = Source.fromFile(file, "UTF-8")
                  try {
                    source.mkString
                  } finally {
                    source.close()
                  }
                } else {
                  ""
                }
                var newContent = content
                val edits = left.getEdits
                edits.forEach((edit) => {
                  val text: String = edit.getNewText
                  val builder = new StringBuilder()
                  builder.append(newContent.substring(0, toOffset(content, edit.getRange.getStart.getLine, edit.getRange.getStart.getCharacter)))
                  builder.append(text)
                  builder.append(newContent.substring(toOffset(content, edit.getRange.getStart.getLine, edit.getRange.getStart.getCharacter)))
                  newContent = builder.toString()
                })
                Files.write(thePath, util.Arrays.asList(newContent), StandardCharsets.UTF_8)
              }
              case None =>
            }
          } else {
            val right = change.getRight
            right match {
              case file: CreateFile => {
                val value = URLUtils.toPath(file.getUri).get
                val container = value.toFile.getParentFile
                if (!container.exists()) {
                  container.mkdirs()
                }
                Files.write(value, "".getBytes(StandardCharsets.UTF_8))
              }
              case file: DeleteFile => {
                URLUtils.toPath(file.getUri).get.toFile.delete()
              }
              case file: RenameFile => {
                Files.move(URLUtils.toPath(file.getOldUri).get, URLUtils.toPath(file.getNewUri).get)
              }
              case _ =>
            }
          }
        })
        CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(true))
      }


      override def publishDiagnostics(diagnostics: PublishDiagnosticsParams): Unit = {
        diagnosticsListener(diagnostics)
      }

      override def showMessage(messageParams: MessageParams): Unit = {
        logMessage(messageParams)
      }

      override def showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture[MessageActionItem] = {
        CompletableFuture.completedFuture(new MessageActionItem("Test"))
      }

      override def logMessage(message: MessageParams): Unit = {
        val value = message.getType match {
          case MessageType.Error => Level.SEVERE
          case MessageType.Warning => Level.WARNING
          case MessageType.Info => Level.INFO
          case MessageType.Log => Level.FINE
        }
        println("[" + value + "]" + message.getMessage)
      }


      override def weaveInputBox(params: WeaveInputBoxParams): CompletableFuture[WeaveInputBoxResult] = {
        CompletableFuture.completedFuture(clientUI.weaveInputBox(params))
      }

      override def weaveQuickPick(params: WeaveQuickPickParams): CompletableFuture[WeaveQuickPickResult] = {
        CompletableFuture.completedFuture(clientUI.weaveQuickPick(params))
      }

      override def openWindow(params: OpenWindowsParams): Unit = {
        clientUI.openWindow(params)
      }

      override def runConfiguration(config: LaunchConfiguration): Unit = {
        clientUI.runConfiguration(config)
      }

      override def openTextDocument(params: OpenTextDocumentParams): Unit = {
        clientUI.openTextDocument(params)
      }

      override def showPreviewResult(result: PreviewResult): Unit = {}

      override def publishDependencies(resolvedDependency: DependenciesParams): Unit = {}

      /**
        * This notification is sent from the server to the client to inform the user that a background job has started.
        *
        * @param job The job information that has started
        */
      override def notifyJobStarted(job: JobStartedParams): Unit = {}

      /**
        * This notification is sent from the server to the client to inform the user that a background job has finish.
        *
        * @param job The job information that has ended
        */
      override def notifyJobEnded(job: JobEndedParams): Unit = {}

      /**
        * This notification is sent from the server to the client to publish current transformation scenarios.
        *
        * @param scenariosParam Scenarios Parameter
        */
      override def showScenarios(scenariosParam: ShowScenariosParams): Unit = {}
    })
  }

  def init(client: WeaveLanguageClient): WeaveLanguageServer = {
    this.lspValue = new WeaveLanguageServer()
    lspValue.connect(client)
    val initializeParams = new InitializeParams()
    initializeParams.setRootUri(workspaceRoot.toUri.toString)
    lspValue.initialize(initializeParams).get()
    lspValue.initialized(new InitializedParams())
    lspValue
  }


}

trait ClientUI {

  def weaveInputBox(params: WeaveInputBoxParams): WeaveInputBoxResult

  def weaveQuickPick(params: WeaveQuickPickParams): WeaveQuickPickResult

  def openWindow(params: OpenWindowsParams): Unit = {}

  def runConfiguration(config: LaunchConfiguration): Unit = {}

  def openTextDocument(params: OpenTextDocumentParams): Unit = {}
}

object DefaultInputsInteraction extends ClientUI {
  override def weaveInputBox(params: WeaveInputBoxParams): WeaveInputBoxResult = {
    WeaveInputBoxResult("")
  }

  override def weaveQuickPick(params: WeaveQuickPickParams): WeaveQuickPickResult = {
    WeaveQuickPickResult(itemsId = util.Arrays.asList(params.items.get(0).id))
  }
}

object DWProject {
  def apply(workspaceDirectory: Path): DWProject = new DWProject(workspaceDirectory)
}
