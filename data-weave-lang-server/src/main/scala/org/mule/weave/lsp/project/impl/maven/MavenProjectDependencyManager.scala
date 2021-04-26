package org.mule.weave.lsp.project.impl.maven

import org.eclipse.lsp4j.FileChangeType
import org.jboss.shrinkwrap.resolver.api.maven.Maven
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact
import org.jboss.shrinkwrap.resolver.impl.maven.MavenStrategyStageImpl
import org.mule.weave.lsp.project.DependencyArtifact
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectDependencyManager
import org.mule.weave.lsp.project.events.DependencyArtifactRemovedEvent
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.URLUtils

import java.io.File

class MavenProjectDependencyManager(project: Project, pomFile: File, eventBus: EventBus, loggerService: ClientLogger) extends ProjectDependencyManager {

  var dependenciesArray: Array[DependencyArtifact] = Array.empty

  override def init(): Unit = {
    reloadArtifacts()
    eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {
      override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
        changeType match {
          case FileChangeType.Changed => {
            val maybeFile = URLUtils.toFile(uri)
            if (maybeFile.exists(_.equals(pomFile))) {
              reloadArtifacts()
            }
          }
          case _ =>
        }
      }
    })
  }

  private def reloadArtifacts(): Unit = {
    try {
      loggerService.logInfo("Loading artifacts from : " + pomFile.getPath)
      val stage: MavenStrategyStageImpl = Maven.configureResolver()
        .loadPomFromFile(pomFile)
        .importRuntimeAndTestDependencies()
        .resolve().asInstanceOf[MavenStrategyStageImpl]
      val mavenWorkingSession = stage.getMavenWorkingSession
      val resolution = mavenWorkingSession.getDependenciesForResolution
      if (resolution != null && !resolution.isEmpty) {
        val dependencies: Array[MavenResolvedArtifact] = stage.withTransitivity().asResolvedArtifact()

        eventBus.fire(new DependencyArtifactRemovedEvent(dependenciesArray))
        dependenciesArray = dependencies.map((a) => {
          DependencyArtifact(a.getCoordinate.toCanonicalForm, a.asFile())
        })
        eventBus.fire(new DependencyArtifactResolvedEvent(dependenciesArray))
        loggerService.logInfo("All dependencies were loaded successfully.")
      } else {
        loggerService.logInfo("No dependency was detected")
        eventBus.fire(new DependencyArtifactRemovedEvent(dependenciesArray))
      }
    } catch {
      case e: Exception => loggerService.logError(s"Exception while resolving dependencies from ${pomFile.getAbsolutePath}", e)
    }
  }

  /**
   * Returns the list of dependencies
   *
   * @return
   */
  def dependencies(): Array[DependencyArtifact] = {
    dependenciesArray
  }
}
