package org.mule.weave.lsp.services.events

import org.mule.weave.lsp.services.events.DocumentChangedEvent.DOCUMENT_CHANGED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFile

class DocumentChangedEvent(vf: VirtualFile) extends Event {
  override type T = OnDocumentChanged

  override def getType: EventType[OnDocumentChanged] = DOCUMENT_CHANGED

  override def dispatch(handler: OnDocumentChanged): Unit = {
    handler.onDocumentChanged(vf)
  }
}

trait OnDocumentChanged extends EventHandler {
  def onDocumentChanged(vf: VirtualFile): Unit
}

object DocumentChangedEvent {
  val DOCUMENT_CHANGED = EventType[OnDocumentChanged]("DOCUMENT_CHANGED")
}