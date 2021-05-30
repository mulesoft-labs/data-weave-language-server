package org.mule.weave.lsp.services.events

import org.mule.weave.lsp.services.events.DocumentOpenedEvent.DOCUMENT_OPENED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFile

class DocumentOpenedEvent(vf: VirtualFile) extends Event {
  override type T = OnDocumentOpened

  override def getType: EventType[OnDocumentOpened] = DOCUMENT_OPENED

  override def dispatch(handler: OnDocumentOpened): Unit = {
    handler.onDocumentOpened(vf)
  }
}

trait OnDocumentOpened extends EventHandler {
  def onDocumentOpened(vf: VirtualFile): Unit
}

object DocumentOpenedEvent {
  val DOCUMENT_OPENED = EventType[OnDocumentOpened]("DOCUMENT_OPENED")
}