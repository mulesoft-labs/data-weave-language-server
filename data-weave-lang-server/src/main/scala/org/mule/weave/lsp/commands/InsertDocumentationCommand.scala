package org.mule.weave.lsp.commands

import org.eclipse.lsp4j
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.services.ValidationService
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.location.WeaveLocation

import java.util

class InsertDocumentationCommand(validationService: ValidationService) extends WeaveCommand {
  override def commandId(): String = Commands.DW_GENERATE_WEAVE_DOC

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val args: util.List[AnyRef] = params.getArguments

    val uri: String = Commands.argAsString(args, 0)
    val startOffset: Int = Commands.argAsInt(args, 1)
    val endOffset: Int = Commands.argAsInt(args, 2)
    val lineNumber: Int = Commands.argAsInt(args, 3)

    val documentToolingService = validationService.documentService().open(uri)
    val docs: Option[String] = documentToolingService.scaffoldDocs(startOffset, endOffset)
    if (docs.isDefined) {

      val applyWorkspaceEditParams = new ApplyWorkspaceEditParams()

      val localChanges = new util.HashMap[String, util.List[TextEdit]]()
      val position = new Position(lineNumber, 0)
      val range = new lsp4j.Range(position, position)
      localChanges.put(uri, util.Arrays.asList(new TextEdit(range, docs.get.trim + "\n")))

      applyWorkspaceEditParams.setEdit(new WorkspaceEdit(localChanges))
      validationService.languageClient().applyEdit(applyWorkspaceEditParams)
    }
    null
  }
}

object InsertDocumentationCommand {
  def createCommand(uri: String, astNode: AstNode): Command = {
    val nodeLocation: WeaveLocation = astNode.location()
    new Command("Generate Weave Documentation",
      Commands.DW_GENERATE_WEAVE_DOC,
      util.Arrays.asList(
        uri,
        nodeLocation.startPosition.index: java.lang.Integer,
        nodeLocation.endPosition.index: java.lang.Integer,
        nodeLocation.startPosition.line - 1: java.lang.Integer,
      )
    )
  }
}
