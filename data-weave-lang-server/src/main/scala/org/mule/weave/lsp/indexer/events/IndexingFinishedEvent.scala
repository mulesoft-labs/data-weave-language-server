package org.mule.weave.lsp.indexer.events


import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent.INDEXING_FINISHED
import org.mule.weave.lsp.utils.{Event, EventHandler, EventType}
import org.mule.weave.v2.editor.VirtualFileSystem


class IndexingFinishedEvent(vfs: VirtualFileSystem) extends Event {
  override type T = OnIndexingFinished

  override def getType: EventType[OnIndexingFinished] = {
    INDEXING_FINISHED
  }

  override def dispatch(handler: OnIndexingFinished): Unit = {
    handler.onIndexingFinished(vfs)
  }

}

object IndexingFinishedEvent {
  val INDEXING_FINISHED: EventType[OnIndexingFinished] = EventType[OnIndexingFinished]("INDEXING_FINISHED")
}

trait OnIndexingFinished extends EventHandler {
  def onIndexingFinished(vfs: VirtualFileSystem)
}
