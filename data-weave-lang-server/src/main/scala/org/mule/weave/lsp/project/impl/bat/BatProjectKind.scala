package org.mule.weave.lsp.project.impl.bat

import org.mule.weave.lsp.bat.BatProjectHelper
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
import org.mule.weave.lsp.project.impl.simple.SimpleDependencyManager
import org.mule.weave.lsp.project.utils.MavenDependencyManagerUtils
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.v2.deps.Artifact

import java.io.File

class BatProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectKind {
  override def name(): String = "BAT"


  override def start(): Unit = {
    val helper = new BatProjectHelper(logger)
    helper.setupBat()
  }

  override def structure(): ProjectStructure = {
    val mainRoot = RootStructure(RootKind.MAIN, Array(new File(project.home(), "src")), Array.empty)
    val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot), Array()))
    components.ProjectStructure(modules, project.home())
  }

  override def dependencyManager(): ProjectDependencyManager = {
    new BatDependencyManager(project, logger, eventBus)
  }

  override def buildManager(): BuildManager = NoBuildManager

  override def sampleDataManager(): Option[SampleDataManager] = None
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

  var batArtifacts: Seq[DependencyArtifact] = Seq.empty

  eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
    override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
      if (modifiedSettingsName.contains(Settings.BAT_VERSION_PROP_NAME)) {
        loadBatVersion()
      }
    }
  })

  override def start(): Unit = {
    super.start()
    loadBatVersion()
  }


  private def loadBatVersion(): Unit = {
    MavenDependencyManagerUtils.MAVEN.retrieve(
      createBATArtifactId(project.settings.batVersion.value()),
      MavenDependencyManagerUtils.callback(eventBus,
        (id: String, artifacts: Seq[Artifact]) => {
          batArtifacts = artifacts.map((artifact) => {
            DependencyArtifact(id, artifact.file)
          })
        }
      ),
      messageCollector)
  }

  private def createBATArtifactId(version: String): String = {
    "com.mulesoft.bat:bdd-core:" + version
  }

  override def dependencies(): Array[DependencyArtifact] = {
    val artifacts: Array[DependencyArtifact] = batArtifacts.toArray
    super.dependencies() ++ artifacts
  }
}