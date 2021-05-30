package org.mule.weave.lsp.services.events

import org.mule.weave.lsp.services.events.DocumentClosedEvent.DOCUMENT_CLOSED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFile

class DocumentClosedEvent(vf: VirtualFile) extends Event {
  override type T = OnDocumentClosed

  override def getType: EventType[OnDocumentClosed] = DOCUMENT_CLOSED

  override def dispatch(handler: OnDocumentClosed): Unit = {
    handler.onDocumentClosed(vf)
  }
}

trait OnDocumentClosed extends EventHandler {
  def onDocumentClosed(vf: VirtualFile): Unit
}

object DocumentClosedEvent {
  val DOCUMENT_CLOSED = EventType[OnDocumentClosed]("DOCUMENT_CLOSED")
}