package org.mule.weave.lsp.bat

import org.mule.weave.lsp.services.ClientLogger

import java.io.File
import java.io.File.separator

class BatProjectHelper(val clientLogger: ClientLogger) extends BatSupport {
  val DEFAULT_BAT_WRAPPER_VERSION = "1.0.58"
  val DEFAULT_BAT_HOME = ".bat"
  val BAT_VERSION_PROP_NAME = "batVersion"
  val BAT_WRAPPER_VERSION_PROP_NAME = "batWrapperVersion"
  val NEXUS: String = "https://repository-master.mulesoft.org/nexus/content/repositories/releases"



  val userHome: String = System.getProperty("user.home")

  override val batHome: File = new File(Array(
    userHome,
    DEFAULT_BAT_HOME
  ).mkString(separator))

  val wrapperFolder: File = new File(batHome.getAbsolutePath + separator + "bat")

  def initBatProject() = {
    if (isBatInstalled) {}
  }
}
