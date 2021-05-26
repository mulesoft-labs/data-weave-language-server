package org.mule.weave.lsp.project.impl.simple

import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.Settings
import org.mule.weave.lsp.project.components
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.ModuleStructure
import org.mule.weave.lsp.project.components.NoBuildManager
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.RootKind
import org.mule.weave.lsp.project.components.RootStructure
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.events.OnSettingsChanged
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.v2.deps.Artifact
import org.mule.weave.v2.deps.DependencyManagerMessageCollector

import java.io.File

class SimpleProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectKind {
  override def name(): String = "Simple"

  override def structure(): ProjectStructure = {
    val mainRoot = RootStructure(RootKind.MAIN, Array(new File(project.home(), "src")), Array.empty)
    val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot)))
    components.ProjectStructure(modules, project.home())
  }

  override def dependencyManager(): ProjectDependencyManager = {
    new SimpleDependencyManager(project, logger, eventBus)
  }

  override def buildManager(): BuildManager = {
    NoBuildManager
  }

  override def sampleDataManager(): Option[SampleDataManager] = None
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

  var weaveArtifacts: Seq[DependencyArtifact] = Seq.empty

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

  override def start(): Unit = {
    loadWeaveVersion()
  }

  private def loadWeaveVersion() = {
    MavenDependencyManagerUtils.MAVEN.retrieve(
      createWLangArtifactId(project.settings.wlangVersion.value()),
      MavenDependencyManagerUtils.callback(eventBus, (id: String, artifacts: Seq[Artifact]) => {
        weaveArtifacts = artifacts.map((artifact) => {
          DependencyArtifact(id, artifact.file)
        })
      }),
      messageCollector)
  }

  private def createWLangArtifactId(version: String) = {
    createWeaveArtifactId("wlang", version)
  }

  private def createWeaveArtifactId(module: String, version: String) = {
    s"org.mule.weave:${module}:${version}"
  }

  override def dependencies(): Array[DependencyArtifact] = {
    weaveArtifacts.toArray
  }
}


