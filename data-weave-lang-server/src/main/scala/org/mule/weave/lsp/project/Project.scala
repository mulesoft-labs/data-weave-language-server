package org.mule.weave.lsp.project

import com.google.gson.JsonObject
import org.eclipse.lsp4j.InitializeParams
import org.mule.weave.lsp.project.Settings._
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils

import java.io.File
import java.util
import scala.collection.mutable.ArrayBuffer

case class Project(url: Option[String], settings: ProjectSettings) {
  private val mayBeHome = url.flatMap((uri) => URLUtils.toFile(uri))

  @transient
  private var projectStarted: Boolean = false

  @transient
  private var indexedValue: Boolean = false

  def home(): File = {
    mayBeHome.get
  }

  def hasHome(): Boolean = {
    mayBeHome.isDefined
  }

  def isStarted(): Boolean = {
    projectStarted
  }

  def markStarted: Unit = projectStarted = true
}

case class ProjectSettings(eventBus: EventBus,
                           wlangVersion: Setting[String] = Setting(WLANG_VERSION_PROP_NAME, DEFAULT_VERSION),
                           languageLevelVersion: Setting[String] = Setting(LANGUAGE_LEVEL_PROP_NAME, DEFAULT_VERSION),
                           validationLevel: Setting[String] = Setting(VALIDATION_LEVEL_PROP_NAME, TYPE_LEVEL),
                           batVersion: Setting[String] = Setting(BAT_VERSION_PROP_NAME, DEFAULT_BAT_VERSION),
                           batWrapperVersion: Setting[String] = Setting(BAT_WRAPPER_VERSION_PROP_NAME, DEFAULT_BAT_WRAPPER_VERSION)
                          ) {

  def load(settings: AnyRef): Array[String] = {
    val allSettings: util.Map[String, AnyRef] = settings.asInstanceOf[util.Map[String, AnyRef]]
    val modifiedProps = new ArrayBuffer[String]()
    if (allSettings != null) {
      val weaveSettings = allSettings.get("data-weave").asInstanceOf[JsonObject]

      if (weaveSettings != null) {
        if (wlangVersion.updateValue(weaveSettings)) {
          modifiedProps.+=(wlangVersion.settingName)
        }
        if (languageLevelVersion.updateValue(weaveSettings)) {
          modifiedProps.+=(languageLevelVersion.settingName)
        }
        if (validationLevel.updateValue(weaveSettings)) {
          modifiedProps.+=(validationLevel.settingName)
        }
        if (batVersion.updateValue(weaveSettings)) {
          modifiedProps.+=(batVersion.settingName)
        }
        if (batWrapperVersion.updateValue(weaveSettings)) {
          modifiedProps.+=(batWrapperVersion.settingName)
        }
      }
    }
    modifiedProps.toArray
  }

  def update(settings: AnyRef): Unit = {
    val modified: Array[String] = load(settings)
    if (modified.nonEmpty) {
      eventBus.fire(new SettingsChangedEvent(modified))
    }
  }
}


object Settings {
  val DEFAULT_VERSION: String = "2.3.1-SNAPSHOT"
  val DEFAULT_BAT_VERSION = "1.0.88"
  val DEFAULT_BAT_WRAPPER_VERSION = "1.0.58"
  val DEFAULT_BAT_HOME = ".bat"
  val TYPE_LEVEL = "type"
  val SCOPE_LEVEL = "scope"
  val PARSE_LEVEL = "parse"
  val WLANG_VERSION_PROP_NAME = "wlangVersion"
  val LANGUAGE_LEVEL_PROP_NAME = "languageLevel"
  val VALIDATION_LEVEL_PROP_NAME = "validationLevel"
  val BAT_VERSION_PROP_NAME = "batVersion"
  val BAT_WRAPPER_VERSION_PROP_NAME = "batWrapperVersion"


  def isTypeLevel(settings: ProjectSettings): Boolean = settings.validationLevel.value() == TYPE_LEVEL

  def isScopeLevel(settings: ProjectSettings): Boolean = settings.validationLevel.value() == SCOPE_LEVEL

  def isParseLevel(settings: ProjectSettings): Boolean = settings.validationLevel.value() == PARSE_LEVEL
}

case class Setting[T <: AnyRef](settingName: String, initialValue: T) {

  private var valueHolder: T = initialValue

  def value(): T = valueHolder

  def updateValue(v: JsonObject): Boolean = {
    Option(v.get(settingName)) match {
      case Some(value) => {
        val newValue = value.getAsString.asInstanceOf[T]
        if (!newValue.equals(valueHolder)) {
          valueHolder = newValue
          true
        } else {
          false
        }
      }
      case None => {
        false
      }
    }
  }

}

object Project {
  def create(initParams: InitializeParams, eventBus: EventBus): Project = {
    val rootPath = Option(initParams.getRootUri).orElse(Option(initParams.getRootPath))
    val settings = ProjectSettings(eventBus)
    settings.load(initParams.getInitializationOptions)
    Project(rootPath, settings)
  }
}