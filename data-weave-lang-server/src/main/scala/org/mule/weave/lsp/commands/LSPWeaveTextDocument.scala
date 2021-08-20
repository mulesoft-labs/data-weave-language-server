package org.mule.weave.lsp.commands

import org.eclipse.lsp4j
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.mule.weave.lsp.utils.LSPConverters.toPosition
import org.mule.weave.v2.completion.Template
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveTextDocument

import java.util

class LSPWeaveTextDocument(toolingService: WeaveDocumentToolingService) extends WeaveTextDocument {

  private val _edits = new util.ArrayList[TextEdit]()

  override def runTemplate(template: Template, location: Int): Unit = {
    insert(template.toLiteralString, location)
  }

  override def insert(text: String, location: Int): Unit = {
    val position: Position = toPosition(toolingService.positionOf(location))
    val textEdit = new TextEdit(new lsp4j.Range(position, position), text)
    _edits.add(textEdit)
  }

  override def delete(startLocation: Int, endLocation: Int): Unit = {
    val textEdit = new TextEdit()
    textEdit.setNewText("")
    val endPosition: Position = toPosition(toolingService.positionOf(endLocation))
    val startPosition: Position = toPosition(toolingService.positionOf(startLocation))
    textEdit.setRange(new org.eclipse.lsp4j.Range(startPosition, endPosition))
    _edits.add(textEdit)
  }

  override def text(startLocation: Int, endLocation: Int): String = {
    toolingService.file.read().substring(startLocation, endLocation)
  }


  override def replace(startLocation: Int, endLocation: Int, newText: String): Unit = {
    val textEdit = new TextEdit()
    textEdit.setNewText(newText)
    val endPosition: Position = toPosition(toolingService.positionOf(endLocation))
    val startPosition: Position = toPosition(toolingService.positionOf(startLocation))
    textEdit.setRange(new org.eclipse.lsp4j.Range(startPosition, endPosition))
    _edits.add(textEdit)
  }

  def edits(): util.ArrayList[TextEdit] = _edits
}
