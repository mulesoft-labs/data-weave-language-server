package org.mule.weave.lsp.project

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

}

object NoProjectKind extends ProjectKind {
  override def name(): String = "NoProject"

  override def structure(): ProjectStructure = ProjectStructure(Array.empty)

  override def dependencyManager(): ProjectDependencyManager = NoDependencyManager

  override def buildManager(): BuildManager = NoBuildManager
}

case class ProjectStructure(modules: Array[ModuleStructure]) {}

case class ModuleStructure(name: String, roots: Array[RootStructure]) {}


object RootKind {
  val MAIN = "main"
  val TEST = "test"
}

/**
  * Represents a root content type. For example in maven
  * src/main -> {kind: main, sources: ["src/main/java","src/main/dw"], resources: [src/main/resources]}
  * src/test -> {kind: test, sources: ["src/test/java","src/test/dwit"], resources: [src/test/resources]}
  */
case class RootStructure(kind: String, sources: Array[File], resources: Array[File]) {}

/**
  * Handles the build process
  */
trait BuildManager {
  def build()

  def deploy()
}

object NoBuildManager extends BuildManager {
  override def build(): Unit = {}

  override def deploy(): Unit = {}
}

/**
  * Handles the
  */
trait ProjectDependencyManager {
  def init(): Unit

}

object NoDependencyManager extends ProjectDependencyManager {
  override def init(): Unit = {}
}

case class DependencyArtifact(artifactId: String, artifact: File)