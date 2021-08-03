package org.mule.weave.lsp.project.components

import java.io.File

/**
  * Handles the artifact resolution and download of all the project dependencies.
  */
trait ProjectDependencyManager {

  /**
    *
    * @return
    */
  def languageLevel(): String

  /**
    * Starts the dependency manager
    */
  def start(): Unit

  /**
    * Returns the list of all the dependencies
    *
    * @return
    */
  def dependencies(): Array[DependencyArtifact]

}

case class DependencyArtifact(artifactId: String, artifact: File)