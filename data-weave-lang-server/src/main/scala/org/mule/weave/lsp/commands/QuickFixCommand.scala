package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.commands.Commands.argAsInt
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.v2.editor.QuickFix
import org.mule.weave.v2.editor.WeaveDocumentToolingService

import java.util

class QuickFixCommand(validationService: DataWeaveToolingService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_QUICK_FIX

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val arguments = params.getArguments
    assert(arguments.size() == 6)
    val uri: String = argAsString(arguments, 0)
    val startOffset: Int = argAsInt(arguments, 1)
    val endOffset: Int = argAsInt(arguments, 2)
    val kind: String = argAsString(arguments, 3)
    val severity: String = argAsString(arguments, 4)
    val qfName: String = argAsString(arguments, 5)

    val quickFixes: Array[QuickFix] = validationService.quickFixesFor(uri, startOffset, endOffset, kind, severity)
    val toolingService: WeaveDocumentToolingService = validationService.openDocument(uri)
    quickFixes.find((qf) => qf.name == qfName).foreach((qf) => {
      val document = new LSPWeaveTextDocument(toolingService)
      qf.quickFix.run(document)
      val edit = new WorkspaceEdit()
      val changes = new util.HashMap[String, util.List[TextEdit]]()
      changes.put(uri, document.edits())
      edit.setChanges(changes)
      validationService.languageClient().applyEdit(new ApplyWorkspaceEditParams(edit, qf.description))
    })
    null
  }

}
