package org.mule.weave.lsp.vfs.events

import org.mule.weave.lsp.utils.{Event, EventHandler, EventType}
import org.mule.weave.lsp.vfs.events.LibrariesModifiedEvent.LIBRARIES_MODIFIED

class LibrariesModifiedEvent extends Event {
  override type T = OnLibrariesModified

  override def getType: EventType[OnLibrariesModified] = {
    LIBRARIES_MODIFIED
  }

  override def dispatch(handler: OnLibrariesModified): Unit = {
    handler.onLibrariesModified()
  }

}

object LibrariesModifiedEvent {
  val LIBRARIES_MODIFIED: EventType[OnLibrariesModified] = EventType[OnLibrariesModified]("LIBRARIES_MODIFIED")
}

trait OnLibrariesModified extends EventHandler {
  def onLibrariesModified()
}
