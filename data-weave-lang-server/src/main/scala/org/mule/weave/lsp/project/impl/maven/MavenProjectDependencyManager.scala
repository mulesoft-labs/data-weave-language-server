package org.mule.weave.lsp.project.impl.maven

import org.eclipse.lsp4j.FileChangeType
import org.jboss.shrinkwrap.resolver.api.maven.Maven
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact
import org.jboss.shrinkwrap.resolver.api.maven.MavenWorkingSession
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency
import org.jboss.shrinkwrap.resolver.impl.maven.MavenStrategyStageImpl
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.events.DependencyArtifactRemovedEvent
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.JavaLoggerForwarder.interceptLog
import org.mule.weave.lsp.utils.URLUtils

import java.io.File
import java.util
import scala.collection.immutable

class MavenProjectDependencyManager(project: Project, pomFile: File, eventBus: EventBus, loggerService: ClientLogger) extends ProjectDependencyManager {

  var dependenciesArray: Array[DependencyArtifact] = Array.empty
  var languageVersion: String = project.settings.wlangVersion.value()

  override def start(): Unit = {
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
      interceptLog(loggerService) {
        val stage: MavenStrategyStageImpl = Maven.configureResolver()
          .loadPomFromFile(pomFile)
          //Import All Scopes until Test and Runtime
          .importDependencies(ScopeType.COMPILE, ScopeType.PROVIDED, ScopeType.RUNTIME, ScopeType.TEST)
          .resolve().asInstanceOf[MavenStrategyStageImpl]
        val mavenWorkingSession: MavenWorkingSession = stage.getMavenWorkingSession
        val resolution: util.List[MavenDependency] = mavenWorkingSession.getDependenciesForResolution
        if (resolution != null && !resolution.isEmpty) {
          val mavenFormatStage: MavenFormatStage = stage.withTransitivity()
          val dependencies: immutable.Iterable[MavenResolvedArtifact] =
            mavenFormatStage
              .asResolvedArtifact()
              .groupBy(_.getCoordinate.toCanonicalForm)
              .map(_._2.head)
          eventBus.fire(new DependencyArtifactRemovedEvent(dependenciesArray))

          languageVersion = dependencies
            .find((d) => d.getCoordinate.getArtifactId.equals("wlang"))
            .map((d) => d.getCoordinate.getVersion)
            .getOrElse(project.settings.wlangVersion.value())

          dependenciesArray = dependencies
            .map((a) => {
              DependencyArtifact(a.getCoordinate.toCanonicalForm, a.asFile())
            }).toArray
          eventBus.fire(new DependencyArtifactResolvedEvent(dependenciesArray))
          loggerService.logInfo("All dependencies were loaded successfully.")
        } else {
          loggerService.logInfo("No dependency was detected.")
          eventBus.fire(new DependencyArtifactRemovedEvent(dependenciesArray))
        }
      }
    } catch {
      case e: Exception => loggerService.logError(s"Exception while resolving dependencies from ${pomFile.getAbsolutePath}", e)
    }
  }

  override def languageLevel(): String = {
    languageVersion
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
