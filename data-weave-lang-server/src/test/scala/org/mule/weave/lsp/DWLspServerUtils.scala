package org.mule.weave.lsp

import java.io.IOException
import java.net.URL
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object DWLspServerUtils {

  def getSimpleProjectWorkspace(): DWProject = {
    getProject("SimpleProject")
  }

  def getMavenProjectWorkspace(): DWProject = {
    getProject("MavenProject")
  }

  def getMavenProjectWithSamplesWorkspace(): DWProject = {
    getProject("MavenProjectWithSamples")
  }

  def getMavenProjectWithUnitTestsWorkspace(): DWProject = {
    getProject("MavenProjectWithUnitTests")
  }

  def getProject(projectName: String): DWProject = {
    val url: URL = getClass.getClassLoader.getResource(s"projects/${projectName}")
    if (url == null) {
      throw new RuntimeException(s"Unable to find project `${projectName}` make sure it is in src/test/resources/projects/${projectName}.`")
    }
    val path = Files.createTempDirectory(projectName)
    val source = Paths.get(url.toURI)
    copyFolder(source, path)
    println("--> Project folder is: " + path)
    DWProject(path)
  }


  @throws[IOException]
  def copyFolder(source: Path, target: Path, options: CopyOption*): Unit = {
    Files.walkFileTree(source, new SimpleFileVisitor[Path]() {
      @throws[IOException]
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.createDirectories(target.resolve(source.relativize(dir)))
        FileVisitResult.CONTINUE
      }

      @throws[IOException]
      override def visitFile(visitFile: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.copy(visitFile, target.resolve(source.relativize(visitFile)))
        FileVisitResult.CONTINUE
      }
    })
  }


}
