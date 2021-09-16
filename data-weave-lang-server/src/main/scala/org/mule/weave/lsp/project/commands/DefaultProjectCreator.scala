package org.mule.weave.lsp.project.commands

import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import scala.io.Source._


class DefaultProjectCreator() extends ProjectCreator {

  def create(projectInfo: ProjectCreationInfo): Unit = {
    val source = fromResource("pom-default.xml")
    val projectPath = projectInfo.pathToCreate.resolve(projectInfo.projectName)
    Files.createDirectories(projectPath)
    val finalPomFile = projectPath.resolve("pom.xml").toFile // Temporary File
    val replacedPom = source.mkString.replaceAll("@group-id@", projectInfo.groupId)
      .replaceAll("@artifact-id@", projectInfo.artifactId)
      .replaceAll("@version@", projectInfo.version)
      .replaceAll("@project-name@", projectInfo.projectName)
    new PrintWriter(finalPomFile) {
      write(replacedPom);
      close
    }
    val sourcePath = projectPath
      .resolve("src")
    val mainPath = sourcePath
      .resolve("main")
    val javaPath = mainPath
      .resolve("java")
    val dwPath = mainPath.resolve("dw")
    val resources = mainPath.resolve("resources")
    val exampleDw = fromResource("dw-template-mapping.dwl").mkString
    val dwFile = dwPath.resolve("MyMapping.dwl")
    val testPath = sourcePath.resolve("test")
    Files.createDirectories(javaPath)
    Files.createDirectories(dwPath)
    Files.createDirectories(resources)
    Files.createDirectories(testPath.resolve("dwit"))
    Files.createDirectories(testPath.resolve("dwmit"))
    Files.createDirectories(testPath.resolve("dwtest"))
    Files.createDirectories(testPath.resolve("java"))
    Files.createDirectories(testPath.resolve("resources"))
    val dwExampleFile = dwFile.toFile
    new FileWriter(dwExampleFile) {
      write(exampleDw)
      close
    }
  }
}

trait ProjectCreator {

  def create(projectInfo: ProjectCreationInfo)
}

case class ProjectCreationInfo(var groupId: String = "", var artifactId: String = "", var version: String = "", var projectName: String = "", var pathToCreate: Path = null)
