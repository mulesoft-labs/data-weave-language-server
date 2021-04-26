package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.services.ValidationServices
import org.mule.weave.v2.parser.ast.header.directives.FunctionDirectiveNode

import java.util

class InsertDocumentationAction(service: ValidationServices) extends CodeActionProvider {
  override def handles(action: CodeActionParams): Boolean = {
    val toolingService = service.documentService().open(action.getTextDocument.getUri)
    val maybeNode = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[FunctionDirectiveNode]))
    maybeNode.exists((astNode) => !astNode.hasWeaveDoc)
  }

  override def actions(action: CodeActionParams): Array[CodeAction] = {
    val toolingService = service.documentService().open(action.getTextDocument.getUri)
    val maybeNode = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[FunctionDirectiveNode]))
    val codeAction = new CodeAction("Generate Weave Documentation")
    val nodeLocation = maybeNode.get.location()
    codeAction.setCommand(
      new Command("Generate Weave Documentation",
        Commands.DW_GENERATE_WEAVE_DOC,
        util.Arrays.asList(
          action.getTextDocument.getUri,
          nodeLocation.startPosition.index: java.lang.Integer,
          nodeLocation.endPosition.index: java.lang.Integer,
          nodeLocation.startPosition.line - 1: java.lang.Integer,
        )
      ))
    Array(codeAction)
  }
}
