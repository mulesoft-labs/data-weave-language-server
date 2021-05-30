package org.mule.weave.lsp.project.components

import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.v2.editor.VirtualFile

import java.io.File

case class ProjectStructure(modules: Array[ModuleStructure], projectHome: File) {}

case class ModuleStructure(name: String, roots: Array[RootStructure], target: Array[TargetFolder] = Array()) {}

object RootKind {
  val MAIN = "main"
  val TEST = "test"
}

object TargetKind {
  val RESOURCES = "resources"
  val CLASS = "class"
  val TEST_RESOURCES = "testResources"
  val TEST_CLASS = "testClass"
}

case class TargetFolder(kind: String, file: Array[File])

/**
  * Represents a root content type. For example in maven
  * src/main -> {kind: main, sources: ["src/main/java","src/main/dw"], resources: [src/main/resources]}
  * src/test -> {kind: test, sources: ["src/test/java","src/test/dwit"], resources: [src/test/resources]}
  */
case class RootStructure(kind: String, sources: Array[File], resources: Array[File]) {}


object ProjectStructure {

  /**
    * Returns true if the Virtual File belongs to a source or resource folder
    *
    * @param vf The virtual file to validate
    * @return True if the file is part of the project
    */
  def isAProjectFile(vf: VirtualFile, projectStructure: ProjectStructure): Boolean = {
    isAProjectFile(vf.url(), projectStructure)
  }

  /**
    * Returns true if the Virtual File belongs to a source or resource folder
    *
    * @param url The url
    * @return True if the file is part of the project
    */
  def isAProjectFile(url: String, projectStructure: ProjectStructure): Boolean = {
    projectStructure.modules.exists((module) => {
      module.roots.exists((root) => {
        URLUtils.isChildOfAny(url, root.sources) || URLUtils.isChildOfAny(url, root.resources)
      })
    })
  }

  def testsSourceFolders(projectStructure: ProjectStructure): Array[File] = {
    projectStructure.modules.flatMap((module) => {
      module.roots.flatMap((root) => {
        if (root.kind == RootKind.TEST) {
          root.sources
        } else {
          Array.empty[File]
        }
      })
    })
  }

  def mainSourceFolders(projectStructure: ProjectStructure): Array[File] = {
    projectStructure.modules.flatMap((module) => {
      module.roots.flatMap((root) => {
        if (root.kind == RootKind.MAIN) {
          root.sources
        } else {
          Array.empty[File]
        }
      })
    })
  }

  def mainTargetFolders(projectStructure: ProjectStructure): Array[File] = {
    projectStructure.modules.flatMap((module) => {
      module.target
        .filter((target) => target.kind == TargetKind.CLASS || target.kind == TargetKind.RESOURCES)
        .flatMap((target) => target.file)
    })
  }
}

