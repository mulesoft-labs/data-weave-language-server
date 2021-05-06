package org.mule.weave.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.mule.weave.lsp.client.OpenWindowsParams
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveInputBoxResult
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.client.WeaveQuickPickParams
import org.mule.weave.lsp.client.WeaveQuickPickResult

import java.nio.file.Path
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors
import scala.collection.mutable
import scala.io.Codec
import scala.io.Source

class DWProject(val workspaceRoot: Path) {

  private val logger: Logger = Logger.getLogger("[" + workspaceRoot.toFile.getName + "]")

  private var lspValue: WeaveLanguageServer = _

  private val diagnosticsValue: mutable.Map[String, PublishDiagnosticsParams] = mutable.Map()

  private val lock = new Object

  def open(relativePath: String): Unit = {
    val filePath = toAbsolutePath(relativePath)
    val item: TextDocumentItem = new TextDocumentItem(filePath.toUri.toString, "DataWeave", 2, toString(filePath))
    val openTextDocumentParams = new DidOpenTextDocumentParams(item)
    lsp().getTextDocumentService.didOpen(openTextDocumentParams)
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
    diagnosticsValue.remove(toAbsolutePath(relativePath).toUri.toString)
  }

  private def toAbsolutePath(relativePath: String) = {
    workspaceRoot.resolve(relativePath).toAbsolutePath.normalize()
  }

  private def lsp() = {
    if (lspValue == null) {
      init((d) => {
        println("[DWProject] Diagnostics for:  " + d.getUri + " --- " + d)
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
      lock.synchronized({
        lock.wait(TimeUnit.HOURS.toNanos(10))
      })
    }
    diagnosticsValue.get(absolutePath)
  }

  def errorsFor(path: String): util.List[Diagnostic] = {
    diagnosticsFor(path).getOrElse(throw new RuntimeException(s"No diagnostics for ${path}.") ).getDiagnostics.stream().filter((d) => {
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
        logger.log(value, message.getMessage)
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

      override def openWindow(params: OpenWindowsParams): Unit = ???
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
