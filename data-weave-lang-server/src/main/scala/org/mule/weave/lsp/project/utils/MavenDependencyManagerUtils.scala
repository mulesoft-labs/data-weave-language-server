package org.mule.weave.lsp.project.utils

import coursier.cache.CacheLogger
import org.mule.weave.lsp.IDEExecutors
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.deps.Artifact
import org.mule.weave.v2.deps.ArtifactResolutionCallback
import org.mule.weave.v2.deps.MavenDependencyManager

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object MavenDependencyManagerUtils {

  private val logger = Logger.getLogger(getClass.getName)

  val MAVEN = new MavenDependencyManager(new File(WeaveDirectoryUtils.getCacheHome(), "maven"),
    IDEExecutors.defaultExecutor(),
    new CacheLogger {
      override def downloadedArtifact(url: String, success: Boolean): Unit = {
        if (success)
          logger.log(Level.INFO, s"Downloaded: ${url}")
      }
    }
  )


  def callback(eventBus: EventBus, onResolved: (String, Seq[Artifact]) => Unit): ArtifactResolutionCallback = new ArtifactResolutionCallback {

    override def shouldDownload(id: String, kind: String): Boolean = true

    override def downloaded(id: String, kind: String, artifact: Future[Seq[Artifact]]): Unit = {
      Await.result(artifact, Duration.Inf).foreach((art) => {
        onResolved(id, Seq(art))
        eventBus.fire(new DependencyArtifactResolvedEvent(Array(DependencyArtifact(id, art.file))))
      })
    }
  }
}
