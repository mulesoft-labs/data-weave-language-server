package org.mule.weave.lsp.services.events

import org.mule.weave.lsp.services.events.DocumentSavedEvent.DOCUMENT_SAVED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFile

class DocumentSavedEvent(vf: VirtualFile) extends Event {
  override type T = OnDocumentSaved

  override def getType: EventType[OnDocumentSaved] = DOCUMENT_SAVED

  override def dispatch(handler: OnDocumentSaved): Unit = {
    handler.onDocumentSaved(vf)
  }
}

trait OnDocumentSaved extends EventHandler {
  def onDocumentSaved(vf: VirtualFile): Unit
}

object DocumentSavedEvent {
  val DOCUMENT_SAVED = EventType[OnDocumentSaved]("DOCUMENT_SAVED")
}