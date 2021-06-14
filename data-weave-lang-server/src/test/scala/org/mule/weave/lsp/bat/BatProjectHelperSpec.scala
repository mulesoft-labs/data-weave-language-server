package org.mule.weave.lsp.bat

import org.apache.commons.io.FileUtils
import org.mule.weave.lsp.project.Settings.DEFAULT_BAT_HOME
import org.mule.weave.lsp.services.ClientLogger
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import java.io.File
import java.io.File.separator
import java.nio.file.Files

class BatProjectHelperSpec extends FlatSpec with Matchers with BatSupport {

  override val DEFAULT_BAT_WRAPPER_VERSION: String = "1.0.58"
  val userHomeFolder: File = Files.createTempDirectory("tempUserHome").toFile
  userHomeFolder.deleteOnExit()
  val userHome: String = userHomeFolder.getAbsolutePath
  val NEXUS: String = "https://repository-master.mulesoft.org/nexus/content/repositories/releases"

  override val batHome: File = new File(Array(
    userHome,
    DEFAULT_BAT_HOME
  ).mkString(separator))

  val wrapperFolder: File = new File(batHome.getAbsolutePath + separator + "bat")
  batHome.deleteOnExit()


  "BatProjectManager" should "check bat folders ok" in {
    isBatInstalled shouldBe false
  }

  "BatProjectManager" should "download and install bat" in {
    val bool = downloadAndInstall()
    bool shouldBe true
  }

  "BatProjectManager" should "run bat" in {
    FileUtils.deleteDirectory(batHome)
    val bool = downloadAndInstall()
    bool shouldBe true
    run(batHome.getAbsolutePath, Some("--version"))
  }
  override val clientLogger: ClientLogger = new ClientLogger(null)
}
