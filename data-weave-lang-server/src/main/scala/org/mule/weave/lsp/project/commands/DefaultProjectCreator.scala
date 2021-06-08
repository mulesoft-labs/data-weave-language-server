package org.mule.weave.lsp.project.commands

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.net.URI
import java.nio.file.Files
import scala.io.Source._


class DefaultProjectCreator() extends ProjectCreator {

  def create(projectInfo: ProjectCreationInfo) : Unit = {
    val source = fromResource("pom-default.xml")
    val projectPath = new File(projectInfo.pathToCreate.resolve(projectInfo.projectName)).toPath
    Files.createDirectories(projectPath)
    val finalPomFile = projectPath.resolve("pom.xml").toFile // Temporary File
    val replacedPom = source.mkString.replaceAll("@group-id@", projectInfo.groupId)
      .replaceAll("@artifact-id@", projectInfo.artifactId)
      .replaceAll("@version@", projectInfo.version)
      .replaceAll("@project-name@", projectInfo.projectName)
    new PrintWriter(finalPomFile) {
      write(replacedPom); close
    }
    val mainPath = projectPath
      .resolve("src")
      .resolve("main")
    val javaPath = mainPath
      .resolve("java")
    val exampleDw = fromResource("dw-template-module.dwl").mkString
    val javaFile = javaPath.resolve("transformation.dwl")
    Files.createDirectories(javaPath)
    Files.createDirectories(mainPath.resolve("test").resolve("resources"))
    val dwExampleFile = javaFile.toFile
    new FileWriter(dwExampleFile){
      write(exampleDw)
      close
    }
  }
}

trait ProjectCreator{

  def create(projectInfo: ProjectCreationInfo)
}

case class ProjectCreationInfo(var groupId: String = "", var artifactId: String = "", var version: String = "", var projectName: String = "", var pathToCreate: URI = null)
