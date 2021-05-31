package org.mule.weave.lsp.extension.services

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment

@JsonSegment("weave/dependency")
trait DependencyManagerService {

  /**
    * Request from the client to the server to add an artifact
    *
    * @return
    */
  @JsonNotification
  def addArtifact(artifact: ArtifactDefinition): Unit


  /**
    * Request from the client to the server to add an artifact
    *
    * @return
    */
  @JsonNotification
  def removeArtifact(artifact: ArtifactDefinition): Unit
}


case class ArtifactDefinition(id: String)


