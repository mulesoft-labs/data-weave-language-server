package org.mule.weave.lsp.services

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.mule.weave.lsp.utils.LSPConverters._
import org.mule.weave.v2.editor.QuickFix
import org.mule.weave.v2.editor.ValidationMessage
import org.mule.weave.v2.editor.ValidationMessages

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger

/*
* Service that handles validation
*/
class ValidationService(weaveService: LSPToolingServices, executor: Executor, projectDefinition: ProjectDefinition) {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  /**
   * Triggers the validation of the specified document.
   *
   * @param documentUri        The URI to be validated
   * @param onValidationFinish A Callback that is called when the validation finishes
   */
  def triggerValidation(documentUri: String, onValidationFinish: () => Unit = () => {}): Unit = {
    logger.log(Level.INFO, "triggerValidation of: " + documentUri)
    CompletableFuture.runAsync(() => {
      val diagnostics = new util.ArrayList[Diagnostic]
      weaveService.withLanguageLevel(projectDefinition.dwLanguageLevel)

      val messages: ValidationMessages = validate(documentUri)
      messages.errorMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Error))
      })

      messages.warningMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Warning))
      })

      weaveService.languageClient().publishDiagnostics(new PublishDiagnosticsParams(documentUri, diagnostics))
      onValidationFinish()
    }, executor)
  }

  def quickFixesFor(documentUri: String, startOffset: Int, endOffset: Int, kind: String, severity: String): Array[QuickFix] = {
    val messages: ValidationMessages = validate(documentUri)
    val messageFound: Option[ValidationMessage] = if (severity == DiagnosticSeverity.Error.name()) {
      messages.errorMessage.find((m) => {
        matchesMessage(m, kind, startOffset, endOffset)
      })
    } else {
      messages.warningMessage.find((m) => {
        matchesMessage(m, kind, startOffset, endOffset)
      })
    }
    messageFound.map(_.quickFix).getOrElse(Array.empty)

  }


  private def matchesMessage(m: ValidationMessage, kind: String, startOffset: Int, endOffset: Int): Boolean = {
    m.location.startPosition.index == startOffset && m.location.endPosition.index == endOffset && toDiagnosticKind(m) == kind
  }

  /**
   * Executes Weave Validation into the corresponding type level
   *
   * @param documentUri The URI to be validates
   * @return The Validation Messages
   */
  def validate(documentUri: String): ValidationMessages = {
    val messages: ValidationMessages =
      if (projectDefinition.isTypeLevel) {
        weaveService.openDocument(documentUri).typeCheck()
      } else if (projectDefinition.isScopeLevel) {
        weaveService.openDocument(documentUri).scopeCheck()
      } else {
        weaveService.openDocument(documentUri).parseCheck()
      }
    messages
  }
}
