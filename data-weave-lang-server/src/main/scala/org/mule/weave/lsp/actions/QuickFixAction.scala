package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Diagnostic
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.services.LSPToolingServices
import org.mule.weave.v2.editor.QuickFix
import org.mule.weave.v2.editor.WeaveDocumentToolingService

import java.util
import scala.collection.JavaConverters._

class QuickFixAction(weaveService: LSPToolingServices) extends CodeActionProvider {

  override def handles(action: CodeActionParams): Boolean = {
    val only: util.List[String] = action.getContext.getOnly
    (only == null || only.contains(CodeActionKind.QuickFix)) && !action.getContext.getDiagnostics.isEmpty
  }

  override def actions(action: CodeActionParams): Array[CodeAction] = {
    val validationService = weaveService.validationService()
    val uri: String = action.getTextDocument.getUri
    val documentToolingService: WeaveDocumentToolingService = weaveService.documentService().open(uri)
    val diagnostics: util.List[Diagnostic] = action.getContext.getDiagnostics
    diagnostics.asScala.flatMap((d) => {
      val startOffset: Int = documentToolingService.offsetOf(d.getRange.getStart.getLine, d.getRange.getStart.getCharacter)
      val endOffset: Int = documentToolingService.offsetOf(d.getRange.getEnd.getLine, d.getRange.getEnd.getCharacter)
      val errorKind: String = d.getCode.getLeft
      val severity: String = d.getSeverity.name()
      val quickFixes: Array[QuickFix] = validationService.quickFixesFor(uri, startOffset, endOffset, errorKind, severity)
      quickFixes.map((qf) => {
        val codeAction = new CodeAction(qf.name)
        val command = new Command(qf.name, Commands.DW_QUICK_FIX, util.Arrays.asList(
          uri, startOffset: java.lang.Integer, endOffset: java.lang.Integer, errorKind, severity, qf.name
        ))
        codeAction.setCommand(command)
        codeAction
      })
    }).toArray
  }

}
