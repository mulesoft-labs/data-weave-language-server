package org.mule.weave.lsp.project.events

import org.mule.weave.lsp.utils.Event
import org.mule.weave.lsp.utils.EventHandler
import org.mule.weave.lsp.utils.EventType

class SettingsChangedEvent(settingsName: Array[String]) extends Event {
  override type T = OnSettingsChanged

  override def getType: EventType[OnSettingsChanged] = SettingsChangedEvent.SETTINGS_CHANGED

  override def dispatch(handler: OnSettingsChanged): Unit = {
    handler.onSettingsChanged(settingsName)
  }

}

/**
 * Called with all the settings that have been modified
 */
trait OnSettingsChanged extends EventHandler {

  def onSettingsChanged(modifiedSettingsName: Array[String]): Unit

}

object SettingsChangedEvent {
  val SETTINGS_CHANGED = EventType[OnSettingsChanged]("SETTINGS_CHANGED")
}
