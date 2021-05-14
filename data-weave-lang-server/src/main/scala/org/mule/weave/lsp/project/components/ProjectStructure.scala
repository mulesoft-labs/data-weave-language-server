package org.mule.weave.lsp.project.components

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


