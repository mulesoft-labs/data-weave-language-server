package org.mule.weave.lsp.project

import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.MetadataProvider
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.impl.bat.BatProjectKindDetector
import org.mule.weave.lsp.project.impl.maven.MavenProjectKindDetector
import org.mule.weave.lsp.project.impl.sfdx.SFDXProjectKindDetector
import org.mule.weave.lsp.project.impl.simple.SimpleProjectKind
import org.mule.weave.lsp.project.impl.simple.SimpleProjectKindDetector
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.EventBus

import java.io.File

/**
  * Detects the Project Kind
  */
trait ProjectKindDetector {

  /**
    * Returns true if this Kind detector handles the specific project
    *
    * @param project The project that we want to check
    * @return true if it is supported
    */
  def supports(project: Project): Boolean


  /**
    * Returns the project kind that was detected
    *
    * @param project The project to be used
    * @return The ProjectKind
    */
  def createKind(project: Project): ProjectKind

}

object ProjectKindDetector {
  def detectProjectKind(project: Project, eventBus: EventBus,
                        clientLogger: ClientLogger,
                        weaveAgentService: WeaveAgentService,
                        weaveLanguageClient: WeaveLanguageClient,
                        weaveScenarioManagerService: WeaveScenarioManagerService,
                        jobManagerService: JobManagerService
                       ): ProjectKind = {
    if (project.hasHome()) {
      val detectors = Seq(
        new MavenProjectKindDetector(eventBus, clientLogger, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService, jobManagerService),
        new BatProjectKindDetector(eventBus, clientLogger, weaveLanguageClient),
        new SFDXProjectKindDetector(eventBus, clientLogger, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService),
        new SimpleProjectKindDetector(eventBus, clientLogger, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService)
      )
      detectors
        .find(_.supports(project))
        .map(_.createKind(project))
        .getOrElse(new SimpleProjectKind(project, clientLogger, eventBus, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService))
    } else {
      new SimpleProjectKind(project, clientLogger, eventBus, weaveAgentService, weaveLanguageClient, weaveScenarioManagerService)
    }
  }
}

import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
  * A Project Kind is the trait that allows us to support multiple kind of projects with different:
  *  - builds
  *  - dependency management
  *  - folder structure
  */
trait ProjectKind {

  /**
    * The name of the kind i.e maven, bat, simple ...
    *
    * @return
    */
  def name(): String

  /**
    * Setups the project kind. Download any tool required for this kind of project
    */
  def start(): Unit = {}

  /**
    * Returns the Project stucture with all the modules and it source folders
    *
    * @return
    */
  def structure(): ProjectStructure

  /**
    * The Dependency Manager handles all the dependencies for this kind of projects
    *
    * @return
    */
  def dependencyManager(): ProjectDependencyManager

  /**
    * Handles the build, deploy of this project
    *
    * @return
    */
  def buildManager(): BuildManager

  /**
    * Handles Scenario Provider for sample data
    *
    * @return The Scenarios For sample data
    */
  def sampleDataManager(): SampleDataManager

  /**
    * Handles the metadata for the files.
    *
    * @return The Metadata Provider if this Project can provide any
    */
  def metadataProvider(): Option[MetadataProvider] = None

  /**
    * Returns any additional change that needs to be made when a new file is created
    *
    * @param folder
    * @return
    */
  def newFile(folder: File, name: String): Array[Either[TextDocumentEdit, ResourceOperation]] = Array.empty

}
