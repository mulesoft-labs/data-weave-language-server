package org.mule.weave.lsp.services

import java.util.concurrent.Executor

import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.mule.weave.lsp.ValidationService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService

class WeaveLSPService(documentServiceFactory: () => WeaveToolingService, executor: Executor, val vfs: ProjectVirtualFileSystem) extends LanguageClientAware {

  var client: LanguageClient = _
  var vs: ValidationService = new ValidationService(this, executor)

  lazy val documentService: WeaveToolingService = documentServiceFactory()


  def openDocument(uri: String): WeaveDocumentToolingService = {
    documentService.open(uri, ImplicitInput(), None)
  }

  override def connect(client: LanguageClient): Unit = {
    this.client = client
  }

  def validationService(): ValidationService = {
    vs
  }

}
