package org.mule.weave.lsp

import java.net.URL
import java.nio.file.Paths

object DWLspServerUtils {

  def getSimpleProjectWorkspace(): DWProject = {
    getProject("SimpleProject")
  }


  def getMavenProjectWorkspace(): DWProject = {
    getProject("MavenProject")
  }

  def getProject(projectName: String): DWProject = {
    val url: URL = getClass.getClassLoader.getResource(s"projects/${projectName}")
    if (url == null) {
      throw new RuntimeException(s"Unable to find project `${projectName}` make sure it is in src/test/resources/projects/${projectName}.`")
    }
    DWProject(Paths.get(url.toURI))
  }


}
