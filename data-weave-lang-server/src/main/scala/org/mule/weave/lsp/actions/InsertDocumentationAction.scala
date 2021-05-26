package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.mule.weave.lsp.commands.InsertDocumentationCommand
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.ast.header.directives.FunctionDirectiveNode

class InsertDocumentationAction(service: DataWeaveToolingService) extends CodeActionProvider {
  override def handles(action: CodeActionParams): Boolean = {
    val toolingService: WeaveDocumentToolingService = service.openDocument(action.getTextDocument.getUri)
    val maybeNode: Option[AstNode] = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[FunctionDirectiveNode]))
    maybeNode.exists((astNode) => !astNode.hasWeaveDoc)
  }

  override def actions(action: CodeActionParams): Array[CodeAction] = {
    val toolingService = service.openDocument(action.getTextDocument.getUri)
    val maybeNode = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[FunctionDirectiveNode]))
    val codeAction = new CodeAction("Generate Weave Documentation")
    val astNode = maybeNode.get
    codeAction.setCommand(InsertDocumentationCommand.createCommand(action.getTextDocument.getUri, astNode))
    Array(codeAction)
  }
}
