package org.mule.weave.lsp.commands

import org.eclipse.lsp4j
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.mule.weave.lsp.commands.Commands.argAsInt
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.services.ValidationService
import org.mule.weave.lsp.utils.LSPConverters.toPosition
import org.mule.weave.v2.completion.Template
import org.mule.weave.v2.editor.QuickFix
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveTextDocument

import java.util

class QuickFixCommand(validationService: ValidationService) extends WeaveCommand {

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
    val toolingService: WeaveDocumentToolingService = validationService.documentService().open(uri)
    quickFixes.find((qf) => qf.name == qfName).foreach((qf) => {
      val edit = new WorkspaceEdit()
      val localChanges = new util.ArrayList[TextEdit]()
      qf.quickFix.run(new WeaveTextDocument {
        override def runTemplate(template: Template, location: Int): Unit = {
          insert(template.toLiteralString, location)
        }

        override def insert(text: String, location: Int): Unit = {
          val position: Position = toPosition(toolingService.positionOf(location))
          val textEdit = new TextEdit(new lsp4j.Range(position, position), text)
          localChanges.add(textEdit)
        }

        override def delete(startLocation: Int, endLocation: Int): Unit = {
          val textEdit = new TextEdit()
          textEdit.setNewText("")
          val endPosition: Position = toPosition(toolingService.positionOf(endLocation))
          val startPosition: Position = toPosition(toolingService.positionOf(startLocation))
          textEdit.setRange(new org.eclipse.lsp4j.Range(startPosition, endPosition))
          localChanges.add(textEdit)
        }

        override def text(startLocation: Int, endLocation: Int): String = {
          toolingService.file.read().substring(startLocation, endLocation)
        }
      })

      val changes = new util.HashMap[String, util.List[TextEdit]]()
      changes.put(uri, localChanges)
      edit.setChanges(changes)
      validationService.languageClient().applyEdit(new ApplyWorkspaceEditParams(edit))
    })
    null
  }

}
