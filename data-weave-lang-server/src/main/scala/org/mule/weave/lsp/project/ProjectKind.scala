package org.mule.weave.lsp.project

import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.project.components.NoBuildManager
import org.mule.weave.lsp.project.components.NoDependencyManager
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.SampleDataManager

import org.mule.weave.lsp.project.impl.bat.BatProjectKindDetector
import org.mule.weave.lsp.project.impl.maven.MavenProjectKindDetector
import org.mule.weave.lsp.project.impl.simple.SimpleProjectKindDetector
import org.mule.weave.lsp.services.ClientLogger
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
  def detectProjectKind(project: Project, eventBus: EventBus, clientLogger: ClientLogger): ProjectKind = {
    if (project.hasHome()) {
      val detectors = Seq(
        new MavenProjectKindDetector(eventBus, clientLogger),
        new BatProjectKindDetector(eventBus, clientLogger),
        new SimpleProjectKindDetector(eventBus, clientLogger)
      )
      detectors.find(_.supports(project)).map(_.createKind(project)).getOrElse(NoProjectKind)
    } else {
      NoProjectKind
    }
  }
}


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
  def setup(): Unit = {}

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
  def sampleDataManager(): Option[SampleDataManager]

}

object NoProjectKind extends ProjectKind {
  override def name(): String = "NoProject"

  override def structure(): ProjectStructure = ProjectStructure(Array.empty, new File(""))

  override def dependencyManager(): ProjectDependencyManager = NoDependencyManager

  override def buildManager(): BuildManager = NoBuildManager

  override def sampleDataManager(): Option[SampleDataManager] = None
}
