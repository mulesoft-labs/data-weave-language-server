package org.mule.weave.lsp.bat

import java.io.File
import java.io.File.separator

import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.v2.deps.DependencyManager

class BatProjectManager(dependencyManager: DependencyManager, messageLoggerService: MessageLoggerService) extends BatSupport {
  val DEFAULT_BAT_WRAPPER_VERSION = "1.0.58"
  val DEFAULT_BAT_HOME = ".bat"
  val BAT_VERSION_PROP_NAME = "batVersion"
  val BAT_WRAPPER_VERSION_PROP_NAME = "batWrapperVersion"
  val NEXUS: String = "https://repository-master.mulesoft.org/nexus/content/repositories/releases"

  override val maven: DependencyManager = dependencyManager
  override val logger: MessageLoggerService = messageLoggerService

  val userHome: String = System.getProperty("user.home")

  override val batHome: File = new File(Array(
    userHome,
    DEFAULT_BAT_HOME
  ).mkString(separator))

  val wrapperFolder: File = new File(batHome.getAbsolutePath + separator + "bat")

  def initBatProject() = {
    if (isBatInstalled){}
  }
}
