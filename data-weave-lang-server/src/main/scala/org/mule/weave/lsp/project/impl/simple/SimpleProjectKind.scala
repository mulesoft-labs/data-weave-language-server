package org.mule.weave.lsp.project.impl.simple

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
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.v2.deps.DependencyManagerMessageCollector

import java.io.File

class SimpleProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectKind {
  override def name(): String = "Simple"

  override def structure(): ProjectStructure = {
    val mainRoot = RootStructure(RootKind.MAIN, Array(new File(project.home(), "src")), Array.empty)
    val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot)))
    ProjectStructure(modules)
  }

  override def dependencyManager(): ProjectDependencyManager = {
    new SimpleDependencyManager(project, logger, eventBus)
  }

  override def buildManager(): BuildManager = {
    NoBuildManager
  }
}

class SimpleProjectKindDetector(eventBus: EventBus, logger: ClientLogger) extends ProjectKindDetector {
  override def supports(project: Project): Boolean = {
    new File(project.home(), "src").exists()
  }


  override def createKind(project: Project): ProjectKind = {
    new SimpleProjectKind(project, logger, eventBus)
  }
}

class SimpleDependencyManager(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectDependencyManager {

  val messageCollector = new DependencyManagerMessageCollector() {
    override def onError(id: String, message: String): Unit = {
      logger.logError(s"Unable to resolve ${id}: reason : ${message}")
    }
  }

  eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
    override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
      if (modifiedSettingsName.contains(Settings.WLANG_VERSION_PROP_NAME)) {
        loadWeaveVersion()
      }
    }
  })

  override def init(): Unit = {
    loadWeaveVersion()
  }

  private def loadWeaveVersion() = {
    MavenDependencyManagerUtils.MAVEN.retrieve(
      createWLangArtifactId(project.settings.wlangVersion.value()),
      MavenDependencyManagerUtils.callback(eventBus),
      messageCollector)
  }

  private def createWLangArtifactId(version: String) = {
    createWeaveArtifactId("wlang", version)
  }

  private def createWeaveArtifactId(module: String, version: String) = {
    s"org.mule.weave:${module}:${version}"
  }



}


