package org.mule.weave.lsp

import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.client.LaunchConfiguration
import org.mule.weave.lsp.client.OpenTextDocumentParams
import org.mule.weave.lsp.client.OpenWindowsParams
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveInputBoxResult
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.client.WeaveQuickPickParams
import org.mule.weave.lsp.client.WeaveQuickPickResult
import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent
import org.mule.weave.lsp.indexer.events.OnIndexingFinished
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent

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


  def init(diagnosticsListener: (PublishDiagnosticsParams) => Unit): WeaveLanguageServer = {
    init(new WeaveLanguageClient {
      override def telemetryEvent(`object`: Any): Unit = {
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

      /**
        * Opens an input box to ask the user for input.
        *
        * @return the user provided input. The future can be cancelled, meaning
        *         the input box should be dismissed in the editor.
        */
      override def weaveInputBox(params: WeaveInputBoxParams): CompletableFuture[WeaveInputBoxResult] = ???

      /**
        * Opens an menu to ask the user to pick one of the suggested options.
        *
        * @return the user provided pick. The future can be cancelled, meaning
        *         the input box should be dismissed in the editor.
        */
      override def weaveQuickPick(params: WeaveQuickPickParams): CompletableFuture[WeaveQuickPickResult] = ???

      override def openWindow(params: OpenWindowsParams): Unit = {}

      override def runConfiguration(config: LaunchConfiguration): Unit = {}

      override def openTextDocument(params: OpenTextDocumentParams): Unit = {}
    })
  }

  def init(client: WeaveLanguageClient): WeaveLanguageServer = {
    this.lspValue = new WeaveLanguageServer()
    lspValue.connect(client)
    val initializeParams = new InitializeParams()
    initializeParams.setRootUri(workspaceRoot.toUri.toString)
    lspValue.initialize(initializeParams)
    lspValue
  }


}

object DWProject {
  def apply(workspaceDirectory: Path): DWProject = new DWProject(workspaceDirectory)
}
