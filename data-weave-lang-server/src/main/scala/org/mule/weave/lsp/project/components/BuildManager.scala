package org.mule.weave.lsp.project.components

/**
  * Handles the build process
  */
trait BuildManager {
  /**
    * Executes the build on this project
    */
  def build(): Unit

  /**
    * Deploys the project into the repository
    */
  def deploy(): Unit
}


object NoBuildManager extends BuildManager {
  override def build(): Unit = {}

  override def deploy(): Unit = {}
}

