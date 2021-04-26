package org.mule.weave.lsp.commands

import org.eclipse.lsp4j
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.services.ValidationServices

import java.util

class InsertDocumentationCommand(toolingServices: ValidationServices) extends WeaveCommand {
  override def commandId(): String = Commands.DW_GENERATE_WEAVE_DOC

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val args = params.getArguments
    val uri = Commands.argAsString(args, 0)
    val startOffset = Commands.argAsInt(args, 1)
    val endOffset = Commands.argAsInt(args, 2)
    val lineNumber = Commands.argAsInt(args, 3)
    val documentToolingService = toolingServices.documentService().open(uri)
    val docs: Option[String] = documentToolingService.scaffoldDocs(startOffset, endOffset)
    if (docs.isDefined) {

      val applyWorkspaceEditParams = new ApplyWorkspaceEditParams()

      val localChanges = new util.HashMap[String, util.List[TextEdit]]()
      val position = new Position(lineNumber, 0)
      val range = new lsp4j.Range(position, position)
      localChanges.put(uri, util.Arrays.asList(new TextEdit(range, docs.get.trim + "\n")))

      applyWorkspaceEditParams.setEdit(new WorkspaceEdit(localChanges))
      toolingServices.languageClient().applyEdit(applyWorkspaceEditParams)
    }
    null
  }
}
