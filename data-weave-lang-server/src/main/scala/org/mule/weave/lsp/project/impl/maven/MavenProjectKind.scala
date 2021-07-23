package org.mule.weave.lsp.project.impl.maven

import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.MetadataProvider
import org.mule.weave.lsp.project.components.ModuleStructure
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.RootKind
import org.mule.weave.lsp.project.components.RootStructure
import org.mule.weave.lsp.project.components.SampleBaseMetadataProvider
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.components.TargetFolder
import org.mule.weave.lsp.project.components.TargetKind
import org.mule.weave.lsp.project.components.WTFSampleDataManager
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.EventBus

import java.io.File


class MavenProjectKindDetector(eventBus: EventBus, clientLogger: ClientLogger, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, weaveScenarioManagerService: WeaveScenarioManagerService) extends ProjectKindDetector {

  override def createKind(project: Project): ProjectKind = {
    new MavenProjectKind(project, new File(project.home(), "pom.xml"), eventBus, clientLogger, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService)
  }

  override def supports(project: Project): Boolean = {
    new File(project.home(), "pom.xml").exists()
  }
}

class MavenProjectKind(project: Project, pom: File, eventBus: EventBus, clientLogger: ClientLogger, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, weaveScenarioManagerService: WeaveScenarioManagerService) extends ProjectKind {

  private val projectDependencyManager: ProjectDependencyManager = new MavenProjectDependencyManager(project, pom, eventBus, clientLogger)
  private val mavenBuildManager = new MavenBuildManager(project, pom, clientLogger)
  private val projectStructure: ProjectStructure = createProjectStructure()
  private val dataManager = new WTFSampleDataManager(structure(), project, weaveLanguageClient)
  private val sampleBaseMetadataProvider = new SampleBaseMetadataProvider(weaveAgentService, eventBus, weaveScenarioManagerService)

  override def name(): String = "DW `Maven`"

  override def structure(): ProjectStructure = {
    projectStructure
  }


  private def createProjectStructure() = {
    val projectHome: File = project.home()
    val targetDir: File = new File(projectHome, "target")
    val rootStructures: Array[RootStructure] = Array(mainRoot(projectHome), testRoot(projectHome))
    val targets: Array[TargetFolder] = Array(TargetFolder(TargetKind.CLASS, Array(new File(targetDir, "classes"))))
    ProjectStructure(Array(ModuleStructure(projectHome.getName, rootStructures, targets)))
  }

  private def mainRoot(projectHome: File): RootStructure = {
    val mainDir = new File(projectHome, "src" + File.separator + "main")

    val srcFolders = Array(new File(mainDir, "dw"), new File(mainDir, "java")).filter(_.exists())
    val resourceFolders = Array(new File(mainDir, "resources")).filter(_.exists())

    val rootStructure = RootStructure(RootKind.MAIN, srcFolders, resourceFolders)
    rootStructure
  }

  private def testRoot(projectHome: File): RootStructure = {
    val mainDir = new File(projectHome, "src" + File.separator + "test")
    val srcFolders = Array(new File(mainDir, "dwtest"), new File(mainDir, "dwit"), new File(mainDir, "dwmit")).filter(_.exists())
    val resourceFolders = Array(new File(mainDir, "resources")).filter(_.exists())
    val rootStructure = RootStructure(RootKind.TEST, srcFolders, resourceFolders)
    rootStructure
  }

  override def dependencyManager(): ProjectDependencyManager = {
    projectDependencyManager
  }

  override def buildManager(): BuildManager = {
    mavenBuildManager
  }

  override def sampleDataManager(): SampleDataManager = {
    dataManager
  }

  override def metadataProvider(): Option[MetadataProvider] = {
    if (project.isStarted()) {
      Some(sampleBaseMetadataProvider)
    } else {
      None
    }
  }
}
