package org.mule.weave.lsp.services

import java.io.File
import java.util

import com.google.gson.JsonObject
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.{DidChangeConfigurationParams, InitializeParams, MessageParams, MessageType}
import org.mule.weave.lsp.bat.BatProjectManager
import org.mule.weave.lsp.services.ProjectDefinition._
import org.mule.weave.lsp.utils.RootFolderUtils
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem

import scala.collection.mutable.ArrayBuffer

class ProjectDefinition(librariesVFS: LibrariesVirtualFileSystem, val batProjectManager: BatProjectManager) {

  var client: LanguageClient = _

  var wlangVersion: Property[String] = Property(WLANG_VERSION_PROP_NAME, DEFAULT_VERSION)
  var languageLevelVersion: Property[String] = Property(LANGUAGE_LEVEL_PROP_NAME, DEFAULT_VERSION)
  var validationLevel: Property[String] = Property(VALIDATION_LEVEL_PROP_NAME, TYPE_LEVEL)
  var batVersion: Property[String] = Property(BAT_VERSION_PROP_NAME, DEFAULT_BAT_VERSION)
  var batWrapperVersion: Property[String] = Property(BAT_WRAPPER_VERSION_PROP_NAME, DEFAULT_BAT_WRAPPER_VERSION)
  var params: InitializeParams = _

  private val listeners: ArrayBuffer[(String, PropertyChangeListener)] = ArrayBuffer()

  /**
   * The root folders of all the available sources
   *
   * @return
   */
  def sourceFolder(): Seq[File] = {
    RootFolderUtils.getRootFolder(params).toSeq
  }


  def connect(client: LanguageClient): Unit = {
    this.client = client
  }

  private def loadSettings(setting: JsonObject): Unit = {
    if (setting != null) {
      updateSettings(setting, triggerUpdateNotifications = false)
    }
  }

  def initialize(params: InitializeParams): Unit = {
    val allSettings = params.getInitializationOptions.asInstanceOf[util.Map[String, AnyRef]]
    if (allSettings != null) {
      val weaveSettings = allSettings.get("data-weave").asInstanceOf[JsonObject]
      loadSettings(weaveSettings)
    }
    this.params = params
    val maybeFile = RootFolderUtils.getRootFolder(params)
    maybeFile match {
      case Some(rootFolder) => {
        val batFile = new File(rootFolder, "bat.yaml")
        if (batFile.exists()) { //is a bat project
          initBatProject()
        } else {
          initBasicDataWeaveProject()
        }
      }
      case None => {
        initBasicDataWeaveProject()
      }
    }
  }

  private def initBatProject() = {
    batProjectManager.setupBat()
    //TODO: Parse exchange.json
    loadLibrary(createBATArtifactId(batVersion.value()))
    registerListener(ProjectDefinition.BAT_VERSION_PROP_NAME, new PropertyChangeListener {
      override def onPropertyChanged(pce: PropertyChangeEvent): Unit = {
        librariesVFS.removeLibrary(createBATArtifactId(pce.oldValue.toString))
        loadLibrary(createBATArtifactId(batVersion.value()))
      }
    })
  }

  private def initBasicDataWeaveProject(): ProjectDefinition = {
    loadLibrary(createWLangArtifactId(wlangVersion.value()))
    //    loadLibrary(createDependencyManagerArtifactId(wlangVersion.value()))
    //    loadLibrary(createHttpModuleArtifactId("1.0.0-SNAPSHOT"))

    registerListener(ProjectDefinition.WLANG_VERSION_PROP_NAME, new PropertyChangeListener {
      override def onPropertyChanged(pce: PropertyChangeEvent): Unit = {
        librariesVFS.removeLibrary(createWLangArtifactId(pce.oldValue.toString))
        loadLibrary(createWLangArtifactId(wlangVersion.value()))
      }
    })
  }

  def updateSettings(settings: DidChangeConfigurationParams): Unit = {
    val allSettings = settings.getSettings.asInstanceOf[JsonObject]
    if (allSettings != null) {
      val element: JsonObject = allSettings.get("data-weave").asInstanceOf[JsonObject]
      updateSettings(element)
    }
  }

  def updateSettings(settings: JsonObject): Unit = {
    if (settings != null) {
      updateSettings(settings, triggerUpdateNotifications = true)
    }
  }

  def registerListener(propName: String, propertyChangeListener: PropertyChangeListener): ProjectDefinition = {
    listeners.+=((propName, propertyChangeListener))
    this
  }

  def fireChange(event: PropertyChangeEvent): Unit = {
    listeners.foreach((listener) => {
      if (listener._1.isEmpty || event.name.matches(listener._1))
        listener._2.onPropertyChanged(event)
    })
  }

  private def updateSettings(jsonObject: JsonObject, triggerUpdateNotifications: Boolean): Unit = {
    wlangVersion.updateValue(jsonObject)
    wlangVersion.updateValue(jsonObject)
    wlangVersion.updateValue(jsonObject)
    languageLevelVersion.updateValue(jsonObject)
  }

  def dwLanguageLevel: String = {
    languageLevelVersion.value()
  }

  private def loadLibrary(artifactId: String): Unit = {
    librariesVFS.retrieveMavenArtifact(artifactId, (id, message) => {
      client.logMessage(new MessageParams(MessageType.Error, s"Error while resolving ${id} : \n" + message))
    })
  }

  private def createHttpModuleArtifactId(version: String): String = {
    createWeaveArtifactId("http-module", version)
  }

  private def createDependencyManagerArtifactId(version: String) = {
    createWeaveArtifactId("dependency-manager", version)
  }

  private def createWLangArtifactId(version: String) = {
    createWeaveArtifactId("wlang", version)
  }

  private def createWeaveArtifactId(module: String, version: String) = {
    s"org.mule.weave:${module}:${version}"
  }


  private def createBATArtifactId(version: String): String = {
    "com.mulesoft.bat:bdd-core:" + version
  }

  def isTypeLevel: Boolean = validationLevel.value() == TYPE_LEVEL

  def isScopeLevel: Boolean = validationLevel.value() == SCOPE_LEVEL

  def isParseLevel: Boolean = validationLevel.value() == PARSE_LEVEL


  case class Property[T <: AnyRef](propertyName: String, initialValue: T) {

    private var valueHolder: T = initialValue

    def value(): T = valueHolder

    def updateValue(v: JsonObject): Unit = {
      Option(v.get(propertyName)) match {
        case Some(value) => {
          val newValue = value.getAsString.asInstanceOf[T]
          if (!newValue.equals(valueHolder)) {
            val oldValue: T = valueHolder
            valueHolder = newValue
            fireChange(PropertyChangeEvent(propertyName, oldValue, newValue))
          }
        }
        case None => {}
      }
    }

  }

}


case class PropertyChangeEvent(name: String, oldValue: AnyRef, newValue: AnyRef)

trait PropertyChangeListener {
  def onPropertyChanged(pce: PropertyChangeEvent): Unit
}

object ProjectDefinition {
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
}

