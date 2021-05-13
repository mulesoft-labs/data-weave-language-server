package org.mule.weave.lsp.project.components

/**
  * Handles the build process
  */
trait BuildManager {
  /**
    * Executes the build on this project
    */
  def build()

  /**
    * Deploys the project into the repository
    */
  def deploy()
}


object NoBuildManager extends BuildManager {
  override def build(): Unit = {}

  override def deploy(): Unit = {}
}

