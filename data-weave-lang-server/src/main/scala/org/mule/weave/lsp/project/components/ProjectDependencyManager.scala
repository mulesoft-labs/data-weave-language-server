package org.mule.weave.lsp.project.components

import java.io.File

/**
  * Handles the artifact resolution and download of all the project dependencies.
  */
trait ProjectDependencyManager {
  def init(): Unit

  /**
    * Returns the list of all the dependencies
    *
    * @return
    */
  def dependencies(): Array[DependencyArtifact]

}

object NoDependencyManager extends ProjectDependencyManager {
  override def init(): Unit = {}

  override def dependencies(): Array[DependencyArtifact] = Array.empty
}

case class DependencyArtifact(artifactId: String, artifact: File)