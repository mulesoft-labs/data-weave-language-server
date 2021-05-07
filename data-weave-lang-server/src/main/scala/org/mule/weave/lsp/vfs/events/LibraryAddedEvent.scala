package org.mule.weave.lsp.vfs.events

import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.lsp.vfs.events.LibrariesModifiedEvent.LIBRARIES_MODIFIED

class LibrariesModifiedEvent extends Event {
  override type T = OnLibraryModified

  override def getType: EventType[OnLibraryModified] = {
    LIBRARIES_MODIFIED
  }

  override def dispatch(handler: OnLibraryModified): Unit = {
    handler.onLibrariesModified()
  }

}

object LibrariesModifiedEvent {
  val LIBRARIES_MODIFIED: EventType[OnLibraryModified] = EventType[OnLibraryModified]("LIBRARIES_MODIFIED")
}

trait OnLibraryModified extends EventHandler {
  def onLibrariesModified()
}
