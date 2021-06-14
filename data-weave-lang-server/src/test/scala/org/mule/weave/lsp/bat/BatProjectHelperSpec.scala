package org.mule.weave.lsp.bat

import org.apache.commons.io.FileUtils
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.mule.weave.lsp.extension.client.DependenciesParams
import org.mule.weave.lsp.extension.client.JobEndedParams
import org.mule.weave.lsp.extension.client.JobStartedParams
import org.mule.weave.lsp.extension.client.LaunchConfiguration
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.OpenWindowsParams
import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.extension.client.WeaveQuickPickResult
import org.mule.weave.lsp.project.Settings.DEFAULT_BAT_HOME
import org.mule.weave.lsp.services.ClientLogger
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import java.io.File
import java.io.File.separator
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

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
  override val clientLogger: ClientLogger = new ClientLogger(new LoggerLanguageClient())
}

class LoggerLanguageClient() extends WeaveLanguageClient {

  private val logger: Logger = Logger.getLogger("BatTestClient")

  /**
    * Opens an input box to ask the user for input.
    *
    * @return the user provided input. The future can be cancelled, meaning
    *         the input box should be dismissed in the editor.
    */
  override def weaveInputBox(params: WeaveInputBoxParams): CompletableFuture[WeaveInputBoxResult] = ???

  /**
    * Opens an menu to ask the user to pick one of the suggested options.
    *
    * @return the user provided pick. The future can be cancelled, meaning
    *         the input box should be dismissed in the editor.
    */
  override def weaveQuickPick(params: WeaveQuickPickParams): CompletableFuture[WeaveQuickPickResult] = ???

  /**
    * Opens a folder in a new window
    *
    * @param params
    */
  override def openWindow(params: OpenWindowsParams): Unit = ???

  /**
    * This notification is sent from the server to the client to execute the specified configuration on client side.
    *
    */
  override def runConfiguration(config: LaunchConfiguration): Unit = ???

  /**
    * This notification is sent from the server to the client to open an editor with the specified document uri
    *
    * @param params The document to be opened
    */
  override def openTextDocument(params: OpenTextDocumentParams): Unit = ???

  /**
    * This notification is sent from the server to the client to show the live data of a script
    *
    * @param result The result of executing a script
    */
  override def showPreviewResult(result: PreviewResult): Unit = ???

  /**
    * This notification is sent from the server to the client to publish all the resolved dependencies of this workspace
    *
    * @param resolvedDependency The list of all the resolved dependencies
    */
  override def publishDependencies(resolvedDependency: DependenciesParams): Unit = ???

  /**
    * This notification is sent from the server to the client to inform the user that a background job has started.
    *
    * @param job The job information that has started
    */
  override def notifyJobStarted(job: JobStartedParams): Unit = ???

  /**
    * This notification is sent from the server to the client to inform the user that a background job has finish.
    *
    * @param job The job information that has ended
    */
  override def notifyJobEnded(job: JobEndedParams): Unit = ???

  override def telemetryEvent(o: Any): Unit = ???

  override def publishDiagnostics(publishDiagnosticsParams: PublishDiagnosticsParams): Unit = ???

  override def showMessage(messageParams: MessageParams): Unit = ???

  override def showMessageRequest(showMessageRequestParams: ShowMessageRequestParams): CompletableFuture[MessageActionItem] = ???

  override def logMessage(messageParams: MessageParams): Unit = {
    messageParams.getType match {
      case MessageType.Error => Level.SEVERE
      case MessageType.Warning => Level.WARNING
      case _ => Level.INFO
    }
    logger.log(Level.INFO, messageParams.getMessage)
  }
}
