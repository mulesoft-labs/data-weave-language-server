package org.mule.weave.lsp.project.commands

import org.mule.weave.lsp.extension.client.OpenWindowsParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickItem
import org.mule.weave.lsp.ui.wizard.DefaultWizardBuilder
import org.mule.weave.lsp.ui.wizard.DefaultWizardStepBuilder
import org.mule.weave.lsp.ui.wizard.InputWidgetBuilder
import org.mule.weave.lsp.ui.wizard.QuickPickWidgetBuilder
import org.mule.weave.lsp.ui.wizard.WidgetResult
import org.mule.weave.lsp.utils.Icons
import org.mule.weave.lsp.utils.Messages.NewDwProject

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProjectProvider(client: WeaveLanguageClient, workspaceLocation: URI) {
  val icons = Icons.vscode

  def newProject(): Unit = {

    val orgIdStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(new InputWidgetBuilder(client).title("Organization Id").default("org.mycompany").prompt("Organization ID").result((projectInfo, result) => {
        projectInfo.groupId = result
        projectInfo
      }))

    val artifactIdStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(new InputWidgetBuilder(client).default("example").prompt("Artifact ID").result((projectInfo, result) => {
        projectInfo.artifactId = result
        projectInfo
      }))


    val versionStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(new InputWidgetBuilder(client).default("1.0.0-SNAPSHOT").prompt("Version").result((projectInfo, result) => {
        projectInfo.version = result
        projectInfo
      }))

    val projectNameStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(new InputWidgetBuilder(client).default("example-project").prompt("Project name").result((projectInfo, result) => {
        projectInfo.projectName = result
        projectInfo
      }))

    val pathStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(createFSChooser(Some(new File(workspaceLocation).toPath)))


    val info = new DefaultWizardBuilder[ProjectCreationInfo] //
      .modelProvider(() => ProjectCreationInfo(groupId = "org.mycompany", artifactId = "example", version = "1.0.0-SNAPSHOT", projectName = "example-project", pathToCreate = new File(workspaceLocation).toPath.toUri))
      .step(orgIdStep).step(artifactIdStep).step(versionStep).step(projectNameStep).step(pathStep).create().open()

    new DefaultProjectCreator().create(info)

    askForWindow(info.pathToCreate.resolve(info.projectName))

  }


  private def createFSChooser(from: Option[Path]): QuickPickWidgetBuilder[ProjectCreationInfo] = {

    val widgetBuilder = new QuickPickWidgetBuilder[ProjectCreationInfo](client).result((projectInfo, result) => {
      projectInfo.pathToCreate = projectInfo.pathToCreate.resolve(result.asJava.get(0))
      projectInfo
    })
    askForPath(from).foreach((item) => widgetBuilder.item(item._1, item._2))
    widgetBuilder
  }


  private def askForPath(
                          from: Option[Path]
                        ): List[(WeaveQuickPickItem, ProjectCreationInfo => WidgetResult[ProjectCreationInfo])] = {

    def quickPickDir(filename: String): (WeaveQuickPickItem, ProjectCreationInfo => WidgetResult[ProjectCreationInfo]) = {
      (WeaveQuickPickItem(
        id = filename,
        label = s"${icons.folder} $filename"
      ), (projectInfo: ProjectCreationInfo) => createFSChooser(Some(new File(projectInfo.pathToCreate).toPath)).create().show(projectInfo))
    }

    val paths: List[(WeaveQuickPickItem, ProjectCreationInfo => WidgetResult[ProjectCreationInfo])] = from match {
      case Some(nonRootPath) =>
        Files.list(nonRootPath).iterator().asScala.toList collect {
          case path if path.toFile.isDirectory =>
            quickPickDir(path.toFile.getName)
        }
      case None =>
        File.listRoots.map(file => quickPickDir(file.toString)).toList
    }
    val currentDir =
      (WeaveQuickPickItem(id = "ok", label = s"${icons.check} Ok"), (projectInfo: ProjectCreationInfo) => WidgetResult(cancelled = false, projectInfo, buttonPressedId = ""))
    val parentDir =
      (WeaveQuickPickItem(id = "..", label = s"${icons.folder} .."), (projectInfo: ProjectCreationInfo) => createFSChooser(from.map(_.getParent)).create().show(projectInfo))
    val includeUpAndCurrent =
      if (from.isDefined) List(currentDir, parentDir) else Nil

    includeUpAndCurrent.:::(paths)
  }


  private def askForWindow(projectPath: URI): Future[Unit] = {
    def openWindow(newWindow: Boolean): Unit = {
      val params = OpenWindowsParams(
        projectPath.toString,
        new java.lang.Boolean(newWindow)
      )
      client.openWindow(params)
    }

    client
      .showMessageRequest(NewDwProject.askForNewWindowParams())
      .toScala
      .map {
        case msg if msg == NewDwProject.no =>
          openWindow(newWindow = false)
        case msg if msg == NewDwProject.yes =>
          openWindow(newWindow = true)
        case _ =>
      }
  }


  private def askForName(
                          default: String,
                          prompt: String
                        ): Future[Option[String]] = {
    client
      .weaveInputBox(
        WeaveInputBoxParams(
          prompt = prompt,
          value = default
        )
      )
      .toScala
      .flatMap {
        case name if !name.cancelled && name.value != null =>
          Future.successful(Some(name.value))
        case name if name.cancelled =>
          Future.successful(None)
        // reask if empty
        case _ => askForName(default, prompt)
      }

  }
}
