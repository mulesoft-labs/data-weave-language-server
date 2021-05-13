package org.mule.weave.lsp.vfs.events

import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.lsp.vfs.ArtifactVirtualFileSystem
import org.mule.weave.lsp.vfs.events.LibraryAddedEvent.LIBRARY_ADDED

class LibraryAddedEvent(library: ArtifactVirtualFileSystem) extends Event {
  override type T = OnLibraryAdded

  override def getType: EventType[OnLibraryAdded] = {
    LIBRARY_ADDED
  }

  override def dispatch(handler: OnLibraryAdded): Unit = {
    handler.onLibrariesAdded(library)
  }
}

object LibraryAddedEvent {
  val LIBRARY_ADDED: EventType[OnLibraryAdded] = EventType[OnLibraryAdded]("LIBRARY_ADDED")
}

trait OnLibraryAdded extends EventHandler {
  def onLibrariesAdded(vfs: ArtifactVirtualFileSystem)
}
