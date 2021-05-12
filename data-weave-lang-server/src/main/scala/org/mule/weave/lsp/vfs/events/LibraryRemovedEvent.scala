package org.mule.weave.lsp.vfs.events

import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.lsp.vfs.ArtifactVirtualFileSystem
import org.mule.weave.lsp.vfs.events.LibraryRemovedEvent.LIBRARY_REMOVED
import org.mule.weave.v2.editor.VirtualFileSystem

class LibraryRemovedEvent(lib: ArtifactVirtualFileSystem) extends Event {
  override type T = OnLibraryRemoved

  override def getType: EventType[OnLibraryRemoved] = {
    LIBRARY_REMOVED
  }

  override def dispatch(handler: OnLibraryRemoved): Unit = {
    handler.onLibraryRemoved(lib)
  }
}

object LibraryRemovedEvent {
  val LIBRARY_REMOVED: EventType[OnLibraryRemoved] = EventType[OnLibraryRemoved]("LIBRARY_REMOVED")
}

trait OnLibraryRemoved extends EventHandler {
  def onLibraryRemoved(lib: VirtualFileSystem)
}
