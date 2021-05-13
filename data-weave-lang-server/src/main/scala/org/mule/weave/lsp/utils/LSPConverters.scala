package org.mule.weave.lsp.utils

import org.eclipse.lsp4j
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.mule.weave.v2.editor.ValidationMessage
import org.mule.weave.v2.parser.location.Position
import org.mule.weave.v2.parser.location.WeaveLocation

/**
  * Utilities function to convert DW Tooling Object to LSP Objects
  */
object LSPConverters extends AnyRef {

  implicit def toPosition(endPosition: Position): lsp4j.Position = {
    val position = new lsp4j.Position()
    val column = if (endPosition.column < 0) 0 else endPosition.column - 1
    val line = if (endPosition.line < 0) 0 else endPosition.line - 1
    position.setCharacter(column)
    position.setLine(line)
    position
  }

  implicit def toRange(location: WeaveLocation): lsp4j.Range = {
    new lsp4j.Range(toPosition(location.startPosition), toPosition(location.endPosition))
  }

  def toDiagnostic(message: ValidationMessage, severity: DiagnosticSeverity): Diagnostic = {
    val diagnostic = new Diagnostic(toRange(message.location), message.message.message, severity, "DataWeave : " + message.message.category.name)
    diagnostic.setCode(toDiagnosticKind(message))
    diagnostic
  }

  def toDiagnosticKind(message: ValidationMessage): String = {
    message.message.getClass.getSimpleName
  }
}
