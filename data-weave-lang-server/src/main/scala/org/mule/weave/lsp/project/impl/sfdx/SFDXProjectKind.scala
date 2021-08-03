package org.mule.weave.lsp.project.impl.sfdx

import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.components
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.DefaultSampleDataManager
import org.mule.weave.lsp.project.components.MetadataProvider
import org.mule.weave.lsp.project.components.ModuleStructure
import org.mule.weave.lsp.project.components.NoBuildManager
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.RootKind
import org.mule.weave.lsp.project.components.RootStructure
import org.mule.weave.lsp.project.components.SampleBaseMetadataProvider
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.impl.simple.SimpleDependencyManager
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.EventBus

import java.io.File

class SFDXProjectKind(project: Project, logger: ClientLogger, eventBus: EventBus, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, weaveScenarioManagerService: WeaveScenarioManagerService) extends ProjectKind {
  private val simpleDependencyManager = new SimpleDependencyManager(project, logger, eventBus)
  private val defaultSampleDataManager = new DefaultSampleDataManager(project.home(), weaveLanguageClient)
  private val sampleBaseMetadataProvider = new SampleBaseMetadataProvider(weaveAgentService, eventBus, weaveScenarioManagerService)

  override def name(): String = "DW SFDX"

  override def structure(): ProjectStructure = {
    val mdapi = new File(project.home(), "mdapi")
    val dwHomeFile = new File(mdapi, "dw")
    val mainRoot = RootStructure(RootKind.MAIN, Array(dwHomeFile), Array.empty)
    val modules = Array(ModuleStructure(project.home().getName, Array(mainRoot)))
    components.ProjectStructure(modules)
  }

  override def dependencyManager(): ProjectDependencyManager = {
    simpleDependencyManager
  }

  override def buildManager(): BuildManager = {
    NoBuildManager
  }

  override def sampleDataManager(): SampleDataManager = {
    defaultSampleDataManager
  }

  override def metadataProvider(): Option[MetadataProvider] = {
    Some(sampleBaseMetadataProvider)
  }
}

class SFDXDependencyManager(project: Project, logger: ClientLogger, eventBus: EventBus) extends SimpleDependencyManager(project, logger, eventBus) {
  override def languageLevel(): String = "2.4.0"
}

class SFDXProjectKindDetector(eventBus: EventBus, logger: ClientLogger, weaveAgentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, weaveScenarioManagerService: WeaveScenarioManagerService) extends ProjectKindDetector {
  override def supports(project: Project): Boolean = {
    new File(project.home(), "sfdx-project.json").exists()
  }

  override def createKind(project: Project): ProjectKind = {
    new SFDXProjectKind(project, logger, eventBus, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService)
  }
}

