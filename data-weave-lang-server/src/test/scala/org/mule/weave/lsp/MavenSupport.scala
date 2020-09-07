package org.mule.weave.lsp

import java.io.File
import java.util.concurrent.{ExecutorService, Executors}

import com.google.common.util.concurrent.ThreadFactoryBuilder
import coursier.cache.CacheLogger
import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.lsp.utils.DataWeaveUtils
import org.mule.weave.v2.deps.MavenDependencyManager

trait MavenSupport {

  val executorService: ExecutorService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-%d\"")
      .setDaemon(true)
      .build()
  )

  val logger: MessageLoggerService = new MessageLoggerService

  val dependencyManager = new MavenDependencyManager(new File(DataWeaveUtils.getCacheHome(), "maven"),
    executorService,
    new CacheLogger {
      override def downloadedArtifact(url: String, success: Boolean): Unit = {
        if (success)
          logger.logInfo(s"Downloaded: ${url}")
      }
    }
  )

}
