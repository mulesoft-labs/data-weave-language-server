package org.mule.weave.lsp.services


import java.util.concurrent.Executor

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.mule.weave.lsp.vfs.LibrariesChangeListener
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.lsp.vfs.VFSChangeListener
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.versioncheck.SVersion

class LSPWeaveToolingService(
                              documentServiceFactory: () => WeaveToolingService,
                              executor: Executor,
                              vfs: ProjectVirtualFileSystem,
                              projectDefinition: ProjectDefinition,
                              librariesVirtualFileSystem: LibrariesVirtualFileSystem
                            ) extends LanguageClientAware {

  private var _client: LanguageClient = _
  private val _validationService: ValidationService = new ValidationService(this, executor, projectDefinition)
  private lazy val _documentService: WeaveToolingService = documentServiceFactory()

  {
    vfs.addVFSChangeListener(new VFSChangeListener {
      override def onDeleted(vf: VirtualFile): Unit = {
        validateDependencies(vf)
      }

      override def onChanged(vf: VirtualFile): Unit = {
        if (vf.path().endsWith("dwl")) {
          _validationService.triggerValidation(vf.path())
        }
        validateDependencies(vf)
      }

      override def onCreated(vf: VirtualFile): Unit = {
        _validationService.triggerValidation(vf.path())
        validateDependencies(vf)
      }
    })
  }

  projectDefinition.registerListener(ProjectDefinition.LANGUAGE_LEVEL_PROP_NAME, new PropertyChangeListener {
    override def onPropertyChanged(pce: PropertyChangeEvent): Unit = {
      validateAllEditors()
    }
  })

  private def validateAllEditors(): Unit = {
    documentService().openEditors().foreach((oe) => {
      validationService().triggerValidation(oe.file.path())
    })
  }

  librariesVirtualFileSystem.registerListener(new LibrariesChangeListener {
    override def onLibraryAdded(id: String): Unit = {
      validateAllEditors()
    }

    override def onLibraryRemoved(id: String): Unit = {
      validateAllEditors()
    }
  })

  private def validateDependencies(vf: VirtualFile): Unit = {
    val fileLogicalName: NameIdentifier = vf.getNameIdentifier
    val dependants: Seq[NameIdentifier] = documentService().dependantsOf(fileLogicalName)
    dependants.foreach((ni) => {
      vf.fs().asResourceResolver.resolve(ni) match {
        case Some(resource) => {
          _validationService.triggerValidation(resource.url())
        }
        case None => {
          println("[Error] No resource found for file " + vf.path())
        }
      }
    })
  }

  def logInfo(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Info, message))
    }
  }

  def logWarning(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Warning, message))
    }
  }

  def logError(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Error, message))
    }
  }

  def documentService(): WeaveToolingService = {
    _documentService
  }

  def openDocument(uri: String): WeaveDocumentToolingService = {
    _documentService.open(uri, ImplicitInput(), None)
  }

  def withLanguageLevel(dwLanguageLevel: String): WeaveToolingService = {
    _documentService.updateLanguageLevel(SVersion.fromString(dwLanguageLevel))
  }

  override def connect(client: LanguageClient): Unit = {
    this._client = client
  }

  def validationService(): ValidationService = {
    _validationService
  }

  def client(): LanguageClient = {
    _client
  }

}
