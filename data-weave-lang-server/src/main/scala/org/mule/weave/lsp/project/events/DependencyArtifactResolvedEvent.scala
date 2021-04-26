package org.mule.weave.lsp.project.events


import org.mule.weave.lsp.project.DependencyArtifact
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class DependencyArtifactResolvedEvent(artifacts: Array[DependencyArtifact]) extends Event {

  override type T = OnDependencyArtifactResolved

  override def getType: EventType[OnDependencyArtifactResolved] = {
    DependencyArtifactResolvedEvent.ARTIFACT_RESOLVED
  }

  override def dispatch(handler: OnDependencyArtifactResolved): Unit = {
    handler.onArtifactsResolved(artifacts)
  }
}

trait OnDependencyArtifactResolved extends EventHandler {
  def onArtifactsResolved(artifacts: Array[DependencyArtifact]): Unit
}


object DependencyArtifactResolvedEvent {
  val ARTIFACT_RESOLVED = EventType[OnDependencyArtifactResolved]("ARTIFACT_RESOLVED")
}
