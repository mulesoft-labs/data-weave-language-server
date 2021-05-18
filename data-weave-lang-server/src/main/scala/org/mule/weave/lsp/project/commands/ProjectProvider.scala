package org.mule.weave.lsp.project.commands

import org.mule.weave.lsp.client.OpenWindowsParams
import org.mule.weave.lsp.client.ThemeIcon
import org.mule.weave.lsp.client.WeaveButton
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.client.WeaveQuickPickItem
import org.mule.weave.lsp.client.WeaveQuickPickParams
import org.mule.weave.lsp.utils.Icons
import org.mule.weave.lsp.utils.Messages.NewDwProject
import org.mule.weave.lsp.utils.WeaveEnrichments.OptionFutureTransformer

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
    askForName("org.mycompany","Organization ID")
      .flatMapOption { organizationID =>
        askForName("example", "Artifact ID").mapOptionInside{artifactId => (organizationID,artifactId)}
      }.flatMapOption { case (organizationId, artifactId) =>
        askForName("1.0.0-SNAPSHOT", "Version").mapOptionInside{version => (organizationId,artifactId,version)}
      }.flatMapOption {case (organizationId, artifactId, version) =>
      askForName("example-project", "Project name").mapOptionInside{projectName => (organizationId,artifactId,version,projectName)}
      }.flatMapOption {case (organizationId, artifactId, version, projectName) =>
        askForPath(Some(new File(workspaceLocation).toPath)).mapOptionInside{projectPath =>
          val projectUri = projectPath.toUri
          new ProjectCreator(ProjectCreationInfo(organizationId,artifactId,version,projectName, projectUri)).create()
          askForWindow(projectUri.resolve(projectName))
        }
      }

  }


  private def askForPath(
                          from: Option[Path]
                        ): Future[Option[Path]] = {

    def quickPickDir(filename: String) = {
      WeaveQuickPickItem(
        id = filename,
        label = s"${icons.folder} $filename"
      )
    }

    val paths : List[WeaveQuickPickItem] = from match {
      case Some(nonRootPath) =>
        Files.list(nonRootPath).iterator().asScala.toList collect {
          case path if path.toFile.isDirectory =>
            quickPickDir(path.toFile.getName)
        }
      case None =>
        File.listRoots.map(file => quickPickDir(file.toString)).toList
    }
    val currentDir =
      WeaveQuickPickItem(id = "ok", label = s"${icons.check} Ok")
    val parentDir =
      WeaveQuickPickItem(id = "..", label = s"${icons.folder} ..")
    val includeUpAndCurrent =
      if (from.isDefined) List(currentDir, parentDir) else Nil
    client
      .weaveQuickPick(
        WeaveQuickPickParams(
          includeUpAndCurrent.:::(paths).asJava,
          placeHolder = from.map(_.toString()).getOrElse("")
        )
      )
      .toScala
      .flatMap {
        case path if path.cancelled =>
          Future.successful(None)
        case path if path.itemsId.get(0) == currentDir.id =>
          Future.successful(from)
        case path if path.itemsId.get(0) == parentDir.id =>
          askForPath(from.map(_.getParent))
        case path =>
          from match {
            case Some(nonRootPath) =>
              askForPath(Some(nonRootPath.resolve(path.itemsId.get(0))))
            case None =>
              val newRoot = File
                .listRoots()
                .collectFirst {
                  case root if root.toString == path.itemsId.get(0) =>
                    root.toPath
                }
              askForPath(newRoot)
          }

      }
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
            value = default,
            title = "Titulo Prueba",
            step = 1,
            totalSteps = 2,
            buttons =  List(WeaveButton("debug-start",ThemeIcon("debug-start")), WeaveButton("back",ThemeIcon("arrow-left"))).asJava
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
