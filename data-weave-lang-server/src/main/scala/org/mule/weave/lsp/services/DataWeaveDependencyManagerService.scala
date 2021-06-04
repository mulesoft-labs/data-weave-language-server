package org.mule.weave.lsp.services

import org.mule.weave.lsp.extension.client.DependenciesParams
import org.mule.weave.lsp.extension.client.DependencyDefinition
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.services.ArtifactDefinition
import org.mule.weave.lsp.extension.services.DependencyManagerService
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.ProjectDependencyManager
import org.mule.weave.lsp.project.events.DependencyArtifactRemovedEvent
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.project.events.OnDependencyArtifactRemoved
import org.mule.weave.lsp.project.events.OnDependencyArtifactResolved
import org.mule.weave.lsp.utils.EventBus

import java.util

class DataWeaveDependencyManagerService(weaveLanguageClient: WeaveLanguageClient) extends ToolingService with DependencyManagerService {

  var projectKind: ProjectKind = _

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    val dependencyManager: ProjectDependencyManager = projectKind.dependencyManager()
    eventBus.register(DependencyArtifactResolvedEvent.ARTIFACT_RESOLVED, new OnDependencyArtifactResolved {
      override def onArtifactsResolved(artifacts: Array[DependencyArtifact]): Unit = {
        publishDependencies(dependencyManager)
      }
    })

    eventBus.register(DependencyArtifactRemovedEvent.ARTIFACT_REMOVED, new OnDependencyArtifactRemoved {
      override def onArtifactsRemoved(artifacts: Array[DependencyArtifact]): Unit = {
        publishDependencies(dependencyManager)
      }
    })
  }

  private def publishDependencies(dependencyManager: ProjectDependencyManager): Unit = {
    val resolvedArtifacts: Array[DependencyArtifact] = dependencyManager.dependencies()
    val deps: Array[DependencyDefinition] = resolvedArtifacts.map((artifact) => {
      DependencyDefinition(id = artifact.artifactId, uri = artifact.artifact.toURI.toString)
    })
    weaveLanguageClient.publishDependencies(DependenciesParams(util.Arrays.asList(deps: _*)))
  }

  override def start(): Unit = super.start()

  override def stop(): Unit = super.stop()

  override def addArtifact(artifact: ArtifactDefinition): Unit = {
//    projectKind.dependencyManager().addDependency()
  }

  override def removeArtifact(artifact: ArtifactDefinition): Unit = {
//    projectKind.dependencyManager().removeDependency()
  }
}
