package org.mule.weave.lsp.commands

import org.eclipse.lsp4j
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.ast.functions.FunctionNode
import org.mule.weave.v2.parser.ast.header.directives.FunctionDirectiveNode
import org.mule.weave.v2.parser.ast.header.directives.VarDirective
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.parser.location.WeaveLocation
import org.mule.weave.v2.ts.WeaveType

import java.util

class InsertWeaveTypeCommand(validationService: DataWeaveToolingService, project: Project) extends WeaveCommand {
  override def commandId(): String = Commands.DW_INSERT_WEAVE_TYPE

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val useLiterals = project.settings.useLiteralOnInsertType.value().toBoolean
    val args: util.List[AnyRef] = params.getArguments

    val uri: String = Commands.argAsString(args, 0)
    val line: Int = Commands.argAsInt(args, 1)
    val column: Int = Commands.argAsInt(args, 2)

    val documentToolingService: WeaveDocumentToolingService = validationService.openDocument(uri)
    val maybeFunctionDirective: Option[AstNode] = documentToolingService.nodeAt(line - 1, column - 1, Some(classOf[FunctionDirectiveNode]))

    maybeFunctionDirective match {
      case Some(functionDirectiveNode: FunctionDirectiveNode) => {
        val literal = functionDirectiveNode.literal
        literal match {
          case fv: FunctionNode if (fv.returnType.isEmpty) => {
            val location: WeaveLocation = fv.body.location()
            val weaveType: WeaveType = documentToolingService.typeOf(location.startPosition.index, location.endPosition.index)
            if (weaveType != null) {
              val applyWorkspaceEditParams = new ApplyWorkspaceEditParams()
              val localChanges = new util.HashMap[String, util.List[TextEdit]]()
              val parametersLocation = fv.params.location()
              val position = new Position(parametersLocation.endPosition.line - 1, parametersLocation.endPosition.column - 1)
              val range = new lsp4j.Range(position, position)
              localChanges.put(uri, util.Arrays.asList(new TextEdit(range, ": " + weaveType.toString(prettyPrint = false, namesOnly = true, useLiterals = useLiterals))))
              applyWorkspaceEditParams.setEdit(new WorkspaceEdit(localChanges))
              validationService.languageClient().applyEdit(applyWorkspaceEditParams)
            }
          }
          case _ =>
        }
      }
      case _ => {
        val maybeVarDirective = documentToolingService.nodeAt(line - 1, column - 1, Some(classOf[VarDirective]))
        maybeVarDirective match {
          case Some(varDirective: VarDirective) if (varDirective.wtype.isEmpty) => {
            val location: WeaveLocation = varDirective.value.location()
            val weaveType: WeaveType = documentToolingService.typeOf(location.startPosition.index, location.endPosition.index)
            if (weaveType != null) {
              val applyWorkspaceEditParams = new ApplyWorkspaceEditParams()
              val localChanges = new util.HashMap[String, util.List[TextEdit]]()
              val parametersLocation = varDirective.variable.location()
              val position = new Position(parametersLocation.endPosition.line - 1, parametersLocation.endPosition.column - 1)
              val range = new lsp4j.Range(position, position)

              localChanges.put(uri, util.Arrays.asList(new TextEdit(range, ": " + weaveType.toString(prettyPrint = false, namesOnly = true, useLiterals = useLiterals))))
              applyWorkspaceEditParams.setEdit(new WorkspaceEdit(localChanges))
              validationService.languageClient().applyEdit(applyWorkspaceEditParams)
            }
          }
          case _ =>
        }
      }
    }

    null
  }

}

object InsertWeaveTypeCommand {

  def createCommand(uri: String, nameIdentifier: NameIdentifier): Command = {
    val nodeLocation: WeaveLocation = nameIdentifier.location()
    new Command("Add Type Annotation",
      Commands.DW_INSERT_WEAVE_TYPE,
      util.Arrays.asList(
        uri,
        nodeLocation.startPosition.line: java.lang.Integer,
        nodeLocation.endPosition.column: java.lang.Integer
      )
    )
  }
}




