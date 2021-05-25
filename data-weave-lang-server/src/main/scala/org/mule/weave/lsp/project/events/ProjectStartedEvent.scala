package org.mule.weave.lsp.project.events

import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class ProjectStartedEvent(project: Project) extends Event {

  override type T = OnProjectStarted

  override def getType: EventType[OnProjectStarted] = {
    ProjectStartedEvent.PROJECT_STARTED
  }

  override def dispatch(handler: OnProjectStarted): Unit = {
    handler.onProjectStarted(project)
  }
}

trait OnProjectStarted extends EventHandler {
  def onProjectStarted(project: Project): Unit
}


object ProjectStartedEvent {
  val PROJECT_STARTED = EventType[OnProjectStarted]("PROJECT_STARTED")
}
