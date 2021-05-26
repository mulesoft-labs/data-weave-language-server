package org.mule.weave.lsp.project.service

import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.utils.EventBus

/**
  * A Tooling service is a cross cutting concern to any project kind that has a life cycle.
  * The life cycle is
  * -> Init. When the project is initializing.
  * -> Start. This is executed in a different thread than the init(). This is ideal for starting processes or running log processes
  * -> Stop. When the project is closed
  */
trait ToolingService {

  /**
    * Initializes the service.
    * This is the right place to wire to all events.
    */
  def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {}

  /**
    * Start the service. This is going to be executed in an Async way
    */
  def start(): Unit = {}

  /**
    * Stops the service
    */
  def stop(): Unit = {}

}
