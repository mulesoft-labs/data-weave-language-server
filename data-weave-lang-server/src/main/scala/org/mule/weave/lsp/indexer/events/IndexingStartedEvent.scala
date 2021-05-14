package org.mule.weave.lsp.indexer.events

import org.mule.weave.lsp.indexer.events.IndexingStartedEvent.INDEXING_STARTED
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType
import org.mule.weave.v2.editor.VirtualFileSystem

class IndexingStartedEvent() extends Event {
  override type T = OnIndexingStarted

  override def getType: EventType[OnIndexingStarted] = {
    INDEXING_STARTED
  }

  override def dispatch(handler: OnIndexingStarted): Unit = {
    handler.onIndexingStarted()
  }
}

object IndexingStartedEvent {
  val INDEXING_STARTED: EventType[OnIndexingStarted] = EventType[OnIndexingStarted]("INDEXING_STARTED")
}

trait OnIndexingStarted extends EventHandler {
  def onIndexingStarted()
}
