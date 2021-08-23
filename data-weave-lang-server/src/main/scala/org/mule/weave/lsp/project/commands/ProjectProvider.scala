package org.mule.weave.lsp.project.commands

import org.mule.weave.lsp.extension.client.OpenWindowsParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickItem
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.ui.wizard.DefaultWizardBuilder
import org.mule.weave.lsp.ui.wizard.DefaultWizardStepBuilder
import org.mule.weave.lsp.ui.wizard.InputWidgetBuilder
import org.mule.weave.lsp.ui.wizard.QuickPickWidgetBuilder
import org.mule.weave.lsp.ui.wizard.WidgetResult
import org.mule.weave.lsp.utils.Icons
import org.mule.weave.lsp.utils.Messages.NewDwProject
import org.mule.weave.lsp.utils.WeaveDirectoryUtils

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.seqAsJavaListConverter

class ProjectProvider(client: WeaveLanguageClient, project: Project) {
  val icons = Icons.vscode

  def newProject(): Unit = {

    val createProjectTitle = "Create Project"

    val orgIdWidgetBuilder = new InputWidgetBuilder[ProjectCreationInfo](client).title(createProjectTitle).default("org.mycompany").prompt("Organization ID").result((projectInfo, result) => {
      projectInfo.groupId = result
      projectInfo
    }).selectionProvider(projectInfo => Some(projectInfo.groupId))

    val orgIdStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(orgIdWidgetBuilder)

    val artifactIdWidgetBuilder = new InputWidgetBuilder[ProjectCreationInfo](client).title(createProjectTitle).default("example").prompt("Artifact ID").result((projectInfo, result) => {
      projectInfo.artifactId = result
      projectInfo
    }).selectionProvider(projectInfo => Some(projectInfo.artifactId))

    val artifactIdStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(artifactIdWidgetBuilder)


    val versionWidget: InputWidgetBuilder[ProjectCreationInfo] = new InputWidgetBuilder[ProjectCreationInfo](client).title(createProjectTitle).default("1.0.0-SNAPSHOT").prompt("Version").selectionProvider(projectInfo =>
      Some(projectInfo.version)
    ).result((projectInfo, result) => {
      projectInfo.version = result
      projectInfo
    })

    val versionStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(versionWidget)

    val projectNameWidgetBuilder = new InputWidgetBuilder[ProjectCreationInfo](client).title(createProjectTitle).default("example-project").prompt("Project name").result((projectInfo, result) => {
      projectInfo.projectName = result
      projectInfo
    }).selectionProvider(projectInfo => Some(projectInfo.projectName))

    val projectNameStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(projectNameWidgetBuilder)

    val workspaceLocation: URI = project.url
      .map((url) => new URI(url))
      .getOrElse(WeaveDirectoryUtils.getUserHome().toURI)

    val pathStep = new DefaultWizardStepBuilder[ProjectCreationInfo] //
      .widgetBuilder(createFSChooser().title(createProjectTitle))


    val info = new DefaultWizardBuilder[ProjectCreationInfo] //
      .modelProvider(() => ProjectCreationInfo(groupId = "org.mycompany", artifactId = "example", version = "1.0.0-SNAPSHOT", projectName = "example-project", pathToCreate = new File(workspaceLocation).toPath))
      .step(orgIdStep).step(artifactIdStep).step(versionStep).step(projectNameStep).step(pathStep).create().open()

    new DefaultProjectCreator().create(info)

    askForWindow(info.pathToCreate.resolve(info.projectName).toUri)

  }


  private def createFSChooser(): QuickPickWidgetBuilder[ProjectCreationInfo] = {

    val widgetBuilder = new QuickPickWidgetBuilder[ProjectCreationInfo](client).result((projectInfo, result) => {
      val selectedIds = result.asJava
      if (selectedIds != null && !selectedIds.isEmpty) {
        val chosenPath = selectedIds.get(0)
        if (!chosenPath.equals("ok")) {
          projectInfo.pathToCreate = projectInfo.pathToCreate.resolve(chosenPath)
        }
      }
      projectInfo
    })
    widgetBuilder.itemProvider(projectInfo => askForPath(Some(projectInfo.pathToCreate.toAbsolutePath)))
    widgetBuilder
  }


  private def askForPath(
                          from: Option[Path]
                        ): List[(WeaveQuickPickItem, ProjectCreationInfo => WidgetResult[ProjectCreationInfo])] = {

    def quickPickDir(filename: String): (WeaveQuickPickItem, ProjectCreationInfo => WidgetResult[ProjectCreationInfo]) = {
      (
        WeaveQuickPickItem(
          id = filename,
          label = s"${icons.folder} $filename"
        ), (projectInfo: ProjectCreationInfo) => createFSChooser().create().show(projectInfo))
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
      (WeaveQuickPickItem(id = "..", label = s"${icons.folder} .."), (projectInfo: ProjectCreationInfo) => createFSChooser().create().show(projectInfo))
    val includeUpAndCurrent =
      if (from.isDefined) List(currentDir, parentDir) else Nil

    includeUpAndCurrent.:::(paths)
  }


  private def askForWindow(projectPath: URI): Unit = {
    def openWindow(newWindow: Boolean): Unit = {
      val params = OpenWindowsParams(
        projectPath.toString,
        new java.lang.Boolean(newWindow)
      )
      client.openWindow(params)
    }

    val item = client
      .showMessageRequest(NewDwProject.askForNewWindowParams())
      .get()
      item match {
        case msg if msg == NewDwProject.no =>
          openWindow(newWindow = false)
        case msg if msg == NewDwProject.yes =>
          openWindow(newWindow = true)
        case _ =>
      }
  }
}
