package org.mule.weave.lsp.bat

import java.io.File
import java.io.File.separator
import java.nio.file.Files

import org.mule.weave.lsp.MavenSupport
import org.mule.weave.lsp.services.ProjectDefinition.DEFAULT_BAT_HOME
import org.mule.weave.v2.deps.DependencyManager
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class BatProjectManagerSpec extends FlatSpec with Matchers with MavenSupport with BatSupport {

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
  override val maven: DependencyManager = dependencyManager


  "BatProjectManager" should "check bat folders ok" in {
    isBatInstalled shouldBe false
  }

  "BatProjectManager" should "download and install bat" in {
    val bool = downloadAndInstall()
    bool shouldBe true
  }

  "BatProjectManager" should "run bat" in {
    val bool = downloadAndInstall()
    bool shouldBe true
    run("/tmp",Some("--version"))
  }

}
