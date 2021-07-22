package org.mule.weave.lsp.project.impl.simple

import org.jboss.shrinkwrap.resolver.api.maven.Maven
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact
import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.Settings
import org.mule.weave.lsp.project.components
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.DefaultSampleDataManager
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.MetadataProvider
import org.mule.weave.lsp.project.components.ModuleStructure
import org.mule.weave.lsp.project.components.NoBuildManager
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.RootKind
import org.mule.weave.lsp.project.components.RootStructure
import org.mule.weave.lsp.project.components.SampleBaseMetadataProvider
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.events.DependencyArtifactRemovedEvent
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.project.events.OnSettingsChanged
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.JavaLoggerForwarder.interceptLog
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.deps.DependencyManagerMessageCollector

import java.io.File
import scala.collection.immutable

class SimpleProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient) extends ProjectKind {
  private val simpleDependencyManager = new SimpleDependencyManager(project, logger, eventBus)
  private val defaultSampleDataManager = new DefaultSampleDataManager(WeaveDirectoryUtils.getDWHome(), weaveLanguageClient)

  override def name(): String = "DW `Simple`"

  override def structure(): ProjectStructure = {
    if (project.hasHome()) {
      val mainRoot = RootStructure(RootKind.MAIN, Array(new File(project.home(), "src")), Array.empty)
      val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot)))
      components.ProjectStructure(modules)
    } else {
      components.ProjectStructure(Array.empty)
    }
  }

  override def dependencyManager(): ProjectDependencyManager = {
    simpleDependencyManager
  }

  override def buildManager(): BuildManager = {
    NoBuildManager
  }

  override def sampleDataManager(): Option[SampleDataManager] = {

    Some(defaultSampleDataManager)
  }

  override def metadataProvider(): Option[MetadataProvider] = {
    Some(new SampleBaseMetadataProvider(defaultSampleDataManager, weaveAgentService, eventBus))
  }
}

class SimpleProjectKindDetector(eventBus: EventBus, logger: ClientLogger, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient) extends ProjectKindDetector {
  override def supports(project: Project): Boolean = {
    new File(project.home(), "src").exists()
  }

  override def createKind(project: Project): ProjectKind = {
    new SimpleProjectKind(project, logger, eventBus, weaveAgentService, weaveLanguageClient)
  }
}

class SimpleDependencyManager(project: Project, logger: ClientLogger, eventBus: EventBus) extends ProjectDependencyManager {

  var dependenciesArray: Array[DependencyArtifact] = Array.empty

  val messageCollector = new DependencyManagerMessageCollector() {
    override def onError(id: String, message: String): Unit = {
      logger.logError(s"Unable to resolve ${id}: reason : ${message}")
    }
  }

  eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
    override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
      if (modifiedSettingsName.contains(Settings.WLANG_VERSION_PROP_NAME)) {
        reloadDependencies()
      }
    }
  })

  override def start(): Unit = {
    reloadDependencies()
  }

  protected def reloadDependencies(): Unit = {
    val allArtifacts: Array[DependencyArtifact] = loadNewArtifacts()
    dependenciesArray = allArtifacts
    eventBus.fire(new DependencyArtifactResolvedEvent(dependenciesArray))
  }

  protected def loadNewArtifacts(): Array[DependencyArtifact] = {
    val id: String = project.settings.wlangVersion.value()
    val runtime: Array[DependencyArtifact] = resolveDependency(createRuntimeArtifactId(id))
    val core: Array[DependencyArtifact] = resolveDependency(createCoreModules(id))
    val yaml: Array[DependencyArtifact] = resolveDependency(createYamlModule(id))
    val allArtifacts = runtime ++ core ++ yaml
    allArtifacts
  }

  protected def resolveDependency(artifactID: String): Array[DependencyArtifact] = {
    interceptLog(logger) {
      val mavenFormatStage: MavenFormatStage = Maven.configureResolver()
        .withRemoteRepo("mule-releases", "https://repository-master.mulesoft.org/nexus/content/repositories/releases", "default")
        .withRemoteRepo("mule-snapshots", "https://repository-master.mulesoft.org/nexus/content/repositories/snapshots", "default")
        .withMavenCentralRepo(true)
        .resolve(artifactID)
        .withTransitivity
      val dependencies: immutable.Iterable[MavenResolvedArtifact] =
        mavenFormatStage
          .asResolvedArtifact()
          .groupBy(_.getCoordinate.toCanonicalForm)
          .map(_._2.head)
      eventBus.fire(new DependencyArtifactRemovedEvent(dependenciesArray))
      dependencies.map((a) => {
        DependencyArtifact(a.getCoordinate.toCanonicalForm, a.asFile())
      }).toArray
    }
  }


  private def createRuntimeArtifactId(version: String) = {
    createWeaveArtifactId("runtime", version)
  }

  private def createCoreModules(version: String) = {
    createWeaveArtifactId("core-modules", version)
  }

  private def createYamlModule(version: String) = {
    createWeaveArtifactId("yaml-module", version)
  }

  private def createWeaveArtifactId(module: String, version: String) = {
    s"org.mule.weave:${module}:${version}"
  }

  override def dependencies(): Array[DependencyArtifact] = {
    dependenciesArray
  }
}


