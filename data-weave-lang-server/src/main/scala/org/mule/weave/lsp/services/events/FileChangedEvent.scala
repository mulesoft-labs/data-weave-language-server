package org.mule.weave.lsp.services.events

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.services.events.FileChangedEvent.FILE_CHANGED_EVENT
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class FileChangedEvent(uri: String, changeType: FileChangeType) extends Event {
  override type T = OnFileChanged

  override def getType: EventType[OnFileChanged] = FILE_CHANGED_EVENT

  override def dispatch(handler: OnFileChanged): Unit = {
    handler.onFileChanged(uri, changeType)
  }
}


trait OnFileChanged extends EventHandler {

  def onFileChanged(uri: String, changeType: FileChangeType): Unit

}

object FileChangedEvent {
  val FILE_CHANGED_EVENT = EventType[OnFileChanged]("FILE_CHANGED_EVENT")
}