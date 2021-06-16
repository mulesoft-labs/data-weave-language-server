package org.mule.weave.lsp.services.events

import org.mule.weave.lsp.services.events.DocumentFocusChangedEvent.DOCUMENT_FOCUS_CHANGED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFile


class DocumentFocusChangedEvent(vf: VirtualFile) extends Event {
  override type T = OnDocumentFocused

  override def getType: EventType[OnDocumentFocused] = DOCUMENT_FOCUS_CHANGED

  override def dispatch(handler: OnDocumentFocused): Unit = {
    handler.onDocumentFocused(vf)
  }
}

trait OnDocumentFocused extends EventHandler {
  def onDocumentFocused(vf: VirtualFile): Unit
}

object DocumentFocusChangedEvent {
  val DOCUMENT_FOCUS_CHANGED = EventType[OnDocumentFocused]("DOCUMENT_FOCUS_CHANGED")
}
