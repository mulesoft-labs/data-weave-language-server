package org.mule.weave.lsp.services

import java.util.concurrent.Executor

import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.mule.weave.lsp.ValidationService
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.lsp.vfs.VFSChangeListener
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class LSPWeaveToolingService(documentServiceFactory: () => WeaveToolingService, executor: Executor, val vfs: ProjectVirtualFileSystem) extends LanguageClientAware {

  private var _client: LanguageClient = _
  private val vs: ValidationService = new ValidationService(this, executor)
  private lazy val _documentService: WeaveToolingService = documentServiceFactory()

  {
    vfs.addVFSChangeListener(new VFSChangeListener {
      override def onDeleted(vf: VirtualFile): Unit = {
        validateDependencies(vf)
      }

      override def onChanged(vf: VirtualFile): Unit = {
        if (vf.path().endsWith("dwl")) {
          vs.triggerValidation(vf.path())
        }
        validateDependencies(vf)
      }

      override def onCreated(vf: VirtualFile): Unit = {
        vs.triggerValidation(vf.path())
        validateDependencies(vf)
      }
    })
  }

  private def validateDependencies(vf: VirtualFile): Unit = {
    val fileLogicalName: NameIdentifier = vf.getNameIdentifier
    val dependants: Seq[NameIdentifier] = documentService().dependantsOf(fileLogicalName)
    dependants.foreach((ni) => {
      vf.fs().asResourceResolver.resolve(ni) match {
        case Some(resource) => {
          vs.triggerValidation(resource.url())
        }
        case None => {
          println("[Error] No resource found for file " + vf.path())
        }
      }
    })
  }

  def documentService(): WeaveToolingService = _documentService

  def openDocument(uri: String): WeaveDocumentToolingService = {
    _documentService.open(uri, ImplicitInput(), None)
  }

  override def connect(client: LanguageClient): Unit = {
    this._client = client
  }

  def validationService(): ValidationService = {
    vs
  }

  def client(): LanguageClient = _client

}
