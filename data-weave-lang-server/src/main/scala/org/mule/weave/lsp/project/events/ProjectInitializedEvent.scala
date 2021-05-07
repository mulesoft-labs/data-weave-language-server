package org.mule.weave.lsp.project.events

import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class ProjectInitializedEvent(project: Project) extends Event {

  override type T = OnProjectInitialized

  override def getType: EventType[OnProjectInitialized] = {
    ProjectInitializedEvent.PROJECT_INITIALIZED
  }

  override def dispatch(handler: OnProjectInitialized): Unit = {
    handler.onProjectInitialized(project)
  }
}

trait OnProjectInitialized extends EventHandler {
  def onProjectInitialized(project: Project): Unit
}


object ProjectInitializedEvent {
  val PROJECT_INITIALIZED = EventType[OnProjectInitialized]("PROJECT_INITIALIZED")
}
