package org.mule.weave.lsp.project.impl.maven

import org.mule.weave.lsp.project.BuildManager
import org.mule.weave.lsp.project.ModuleStructure
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectDependencyManager
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.ProjectKindDetector
import org.mule.weave.lsp.project.ProjectStructure
import org.mule.weave.lsp.project.RootKind
import org.mule.weave.lsp.project.RootStructure
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus

import java.io.File


class MavenProjectKindDetector(eventBus: EventBus, clientLogger: ClientLogger) extends ProjectKindDetector {

  override def createKind(project: Project): ProjectKind = {
    new MavenProjectKind(project, new File(project.home(), "pom.xml"), eventBus, clientLogger)
  }

  override def supports(project: Project): Boolean = {
    new File(project.home(), "pom.xml").exists()
  }
}

class MavenProjectKind(project: Project, pom: File, eventBus: EventBus, loggerService: ClientLogger) extends ProjectKind {
  override def name(): String = "Maven"

  override def structure(): ProjectStructure = {
    val projectHome = project.home()
    ProjectStructure(Array(ModuleStructure("", Array(mainRoot(projectHome), testRoot(projectHome)))))
  }

  private def mainRoot(projectHome: File) = {
    val mainDir = new File(projectHome, "src" + File.separator + "main")

    val srcFolders = Array(new File(mainDir, "dw"), new File(mainDir, "java")).filter(_.exists())
    val resourceFolders = Array(new File(mainDir, "resources")).filter(_.exists())

    val rootStructure = RootStructure(RootKind.MAIN, srcFolders, resourceFolders)
    rootStructure
  }

  private def testRoot(projectHome: File) = {
    val mainDir = new File(projectHome, "src" + File.separator + "test")
    val srcFolders = Array(new File(mainDir, "dw"), new File(mainDir, "dwit"), new File(mainDir, "dwmit")).filter(_.exists())
    val resourceFolders = Array(new File(mainDir, "resources")).filter(_.exists())

    val rootStructure = RootStructure(RootKind.TEST, srcFolders, resourceFolders)
    rootStructure
  }

  override def dependencyManager(): ProjectDependencyManager = {
    new MavenProjectDependencyManager(project, pom, eventBus, loggerService)
  }

  override def buildManager(): BuildManager = {
    new MavenBuildManager(project, pom)
  }
}
