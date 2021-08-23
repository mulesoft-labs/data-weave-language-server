package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.services.DataWeaveToolingService

import java.util

class ExtractVariableCommand(weaveService: DataWeaveToolingService) extends WeaveCommand {
  override def commandId(): String = Commands.DW_EXTRACT_VARIABLE

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val startOffset = Commands.argAsInt(params.getArguments, 0)
    val endOffset = Commands.argAsInt(params.getArguments, 1)
    val uri = Commands.argAsString(params.getArguments, 2)
    val toolingService = weaveService.openDocument(uri)
    val maybeRefactor = toolingService.extractVariable(startOffset, endOffset)
    maybeRefactor match {
      case Some(refactor) => {
        val document = new LSPWeaveTextDocument(toolingService)
        refactor.run(document, refactor.parameters().map((p) => (p.name, p.defaultValue)).toMap)
        val edit = new WorkspaceEdit()
        val changes = new util.HashMap[String, util.List[TextEdit]]()
        changes.put(uri, document.edits())
        edit.setChanges(changes)
        val response = weaveService.languageClient().applyEdit(new ApplyWorkspaceEditParams(edit, "Extract Variable")).get()
        response.isApplied
      }
      case None =>
    }
    null
  }

}

object ExtractVariableCommand {
  def createCommand(startOffset: Int, endOffset: Int, uri: String): Command = {
    new Command("Extract Variable", Commands.DW_EXTRACT_VARIABLE,
      util.Arrays.asList[AnyRef](
        startOffset: java.lang.Integer,
        endOffset: java.lang.Integer,
        uri
      ))
  }
}
