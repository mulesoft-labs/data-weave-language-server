package org.mule.weave.lsp.project.impl.maven

import org.mule.weave.lsp.project.BuildManager
import org.mule.weave.lsp.project.Project

import java.io.File

class MavenBuildManager(project: Project, pom: File) extends BuildManager {
  override def build(): Unit = ???

  override def deploy(): Unit = ???
}
