package org.mule.weave.lsp.project.events

import org.mule.weave.lsp.project.DependencyArtifact
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class DependencyArtifactRemovedEvent(artifacts: Array[DependencyArtifact]) extends Event {

  override type T = OnDependencyArtifactRemoved

  override def getType: EventType[OnDependencyArtifactRemoved] = {
    DependencyArtifactRemovedEvent.ARTIFACT_REMOVED
  }

  override def dispatch(handler: OnDependencyArtifactRemoved): Unit = {
    handler.onArtifactsRemoved(artifacts)
  }
}

trait OnDependencyArtifactRemoved extends EventHandler {
  def onArtifactsRemoved(artifacts: Array[DependencyArtifact]): Unit
}


object DependencyArtifactRemovedEvent {
  val ARTIFACT_REMOVED = EventType[OnDependencyArtifactRemoved]("ARTIFACT_REMOVED")
}
