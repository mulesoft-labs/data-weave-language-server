package org.mule.weave.lsp.actions

import org.eclipse.lsp4j
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Position
import org.mule.weave.lsp.commands.ExtractVariableCommand
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.v2.editor.WeaveDocumentToolingService

class RefactorActionProvider(weaveService: DataWeaveToolingService) extends CodeActionProvider {
  override def handles(action: CodeActionParams): Boolean = {
    val range: lsp4j.Range = action.getRange
    val startPosition: Position = range.getStart
    val endPosition: Position = range.getEnd

    val weaveDocumentToolingService: WeaveDocumentToolingService = weaveService.openDocument(action.getTextDocument.getUri, true)
    val startOffset: Int = toOffset(weaveDocumentToolingService, startPosition)
    val endOffset: Int = toOffset(weaveDocumentToolingService, endPosition)
    weaveDocumentToolingService.nodeAt(startOffset, endOffset).isDefined
  }

  private def toOffset(weaveDocumentToolingService: WeaveDocumentToolingService, startPosition: Position) = {
    weaveDocumentToolingService.offsetOf(startPosition.getLine, startPosition.getCharacter)
  }

  override def actions(action: CodeActionParams): Array[CodeAction] = {
    val range: lsp4j.Range = action.getRange
    val startPosition: Position = range.getStart
    val endPosition: Position = range.getEnd
    val weaveDocumentToolingService: WeaveDocumentToolingService = weaveService.openDocument(action.getTextDocument.getUri, true)
    val startOffset: Int = toOffset(weaveDocumentToolingService, startPosition)
    val endOffset: Int = toOffset(weaveDocumentToolingService, endPosition)
    val codeAction = new CodeAction("Extract Variable")
    codeAction.setCommand(ExtractVariableCommand.createCommand(startOffset, endOffset, action.getTextDocument.getUri))
    codeAction.setKind(CodeActionKind.RefactorExtract + ".constant")
    Array(codeAction)
  }
}
