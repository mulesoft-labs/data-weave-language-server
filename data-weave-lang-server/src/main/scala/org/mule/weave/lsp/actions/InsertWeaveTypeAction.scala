package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.mule.weave.lsp.commands.InsertWeaveTypeCommand
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.ast.functions.FunctionNode
import org.mule.weave.v2.parser.ast.header.directives.FunctionDirectiveNode
import org.mule.weave.v2.parser.ast.header.directives.VarDirective
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class InsertWeaveTypeAction(service: DataWeaveToolingService) extends CodeActionProvider {
  override def handles(action: CodeActionParams): Boolean = {
    val toolingService: WeaveDocumentToolingService = service.openDocument(action.getTextDocument.getUri)
    val maybeNode: Option[AstNode] = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[FunctionDirectiveNode]))
    maybeNode
      .orElse({
        toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[VarDirective]))
      })
      .collect({
        case functionDirectiveNode: FunctionDirectiveNode => functionDirectiveNode.literal
        case varDirective: VarDirective => varDirective
      })
      .exists((astNode) => {
        astNode match {
          case fn: FunctionNode => fn.returnType.isEmpty
          case fn: VarDirective => fn.wtype.isEmpty
          case _ => false
        }
      })
  }

  override def actions(action: CodeActionParams): Array[CodeAction] = {
    val toolingService = service.openDocument(action.getTextDocument.getUri)
    val maybeNode = toolingService.nodeAt(action.getRange.getStart.getLine, action.getRange.getStart.getCharacter, Some(classOf[NameIdentifier]))
    val codeAction = new CodeAction("Add Type Annotation")
    maybeNode match {
      case Some(astNode: NameIdentifier) => {
        codeAction.setCommand(InsertWeaveTypeCommand.createCommand(action.getTextDocument.getUri, astNode))
        Array(codeAction)
      }
      case _ => {
        Array.empty
      }
    }

  }
}
