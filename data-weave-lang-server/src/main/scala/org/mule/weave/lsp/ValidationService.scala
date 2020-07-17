package org.mule.weave.lsp

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.mule.weave.lsp.LSPConverters._
import org.mule.weave.lsp.services.LSPWeaveToolingService

/*
* Service that handles validation
*/
class ValidationService(weaveService: LSPWeaveToolingService, executor: Executor) {

  def triggerValidation(documentUri: String): Unit = {
    CompletableFuture.runAsync(() => {
      val diagnostics = new util.ArrayList[Diagnostic]
      val messages = weaveService.openDocument(documentUri).typeCheck()
      messages.errorMessage.foreach((message) => {
        diagnostics.add(new Diagnostic(toRange(message.location), message.message.message, DiagnosticSeverity.Error, "DataWeave : " + message.message.category.name))
      })

      messages.warningMessage.foreach((message) => {
        diagnostics.add(new Diagnostic(toRange(message.location), message.message.message, DiagnosticSeverity.Warning, "DataWeave : " + message.message.category.name))
      })
      weaveService.client().publishDiagnostics(new PublishDiagnosticsParams(documentUri, diagnostics))
    }, executor)
  }
}
