package org.mule.weave.lsp.services.delegate

import org.mule.weave.lsp.extension.services.ArtifactDefinition
import org.mule.weave.lsp.extension.services.DependencyManagerService

class DependencyManagerServiceDelegate extends DependencyManagerService {

  var delegate: DependencyManagerService = _

  override def addArtifact(artifact: ArtifactDefinition): Unit = {
    if (delegate != null)
      delegate.addArtifact(artifact)
  }

  override def removeArtifact(artifact: ArtifactDefinition): Unit = {
    if (delegate != null)
      delegate.removeArtifact(artifact)
  }
}
