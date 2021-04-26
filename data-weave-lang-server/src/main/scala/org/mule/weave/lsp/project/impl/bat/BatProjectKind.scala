package org.mule.weave.lsp.project.impl.bat

import org.mule.weave.lsp.bat.BatProjectHelper
import org.mule.weave.lsp.project.BuildManager
import org.mule.weave.lsp.project.DependencyArtifact
import org.mule.weave.lsp.project.ModuleStructure
import org.mule.weave.lsp.project.NoBuildManager
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectDependencyManager
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.ProjectStructure
import org.mule.weave.lsp.project.RootKind
import org.mule.weave.lsp.project.RootStructure
import org.mule.weave.lsp.project.Settings
import org.mule.weave.lsp.project.events.OnSettingsChanged
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.project.impl.simple.SimpleDependencyManager
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus

import java.io.File

class BatProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectKind {
  override def name(): String = "BAT"


  override def init(): Unit = {
    val helper = new BatProjectHelper(logger)
    helper.setupBat()
  }

  override def structure(): ProjectStructure = {
    val mainRoot = RootStructure(RootKind.MAIN, Array(new File(project.home(), "src")), Array.empty)
    val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot)))
    ProjectStructure(modules)
  }

  override def dependencyManager(): ProjectDependencyManager = {
    new BatDependencyManager(project, logger, eventBus)
  }

  override def buildManager(): BuildManager = NoBuildManager
}

class BatProjectKindDetector(eventBus: EventBus, logger: ClientLogger) extends ProjectKindDetector {
  override def supports(project: Project): Boolean = {
    new File(project.home(), "exchange.json").exists()
  }

  override def createKind(project: Project): ProjectKind = {
    new BatProjectKind(project, logger, eventBus)
  }
}


class BatDependencyManager(project: Project, logger: ClientLogger, eventBus: EventBus) extends SimpleDependencyManager(project, logger, eventBus) {


  eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
    override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
      if (modifiedSettingsName.contains(Settings.BAT_VERSION_PROP_NAME)) {
        loadBatVersion()
      }
    }
  })

  override def init(): Unit = {
    super.init()
    loadBatVersion()
  }


  private def loadBatVersion() = {
    MavenDependencyManagerUtils.MAVEN.retrieve(
      createBATArtifactId(project.settings.batVersion.value()),
      MavenDependencyManagerUtils.callback(eventBus),
      messageCollector)
  }

  private def createBATArtifactId(version: String): String = {
    "com.mulesoft.bat:bdd-core:" + version
  }


}