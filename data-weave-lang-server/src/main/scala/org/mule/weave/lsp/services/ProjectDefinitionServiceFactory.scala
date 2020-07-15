package org.mule.weave.lsp.services

import org.eclipse.lsp4j.InitializeParams

class ProjectDefinitionServiceFactory {

  def createProjectDefinition(params:InitializeParams): ProjectDefinitionService = {
    return new DefaultProjectDefinitionService(params)
  }

}
