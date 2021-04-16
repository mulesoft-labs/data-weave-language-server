package org.mule.weave.lsp.services


import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.mule.weave.dsp.DataWeaveDebuggerAdapterProtocolLauncher.getClass
import org.mule.weave.lsp.vfs.LibrariesChangeListener
import org.mule.weave.lsp.vfs.LibrariesVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.versioncheck.SVersion

import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger

class LSPToolingServices(
                              documentServiceFactory: () => WeaveToolingService,
                              executor: Executor,
                              vfs: ProjectVirtualFileSystem,
                              projectDefinition: ProjectDefinition,
                              librariesVirtualFileSystem: LibrariesVirtualFileSystem
                            ) extends LanguageClientAware {

  private val logger: Logger = Logger.getLogger(getClass.getName)
  private var _client: LanguageClient = _
  private val _validationService: ValidationService = new ValidationService(this, executor, projectDefinition)
  private lazy val _documentService: WeaveToolingService = documentServiceFactory()

  {
    vfs.changeListener(new ChangeListener {
      override def onDeleted(vf: VirtualFile): Unit = {
        validateDependencies(vf)
      }

      override def onChanged(vf: VirtualFile): Unit = {
        if (vf.url().endsWith("dwl")) {
          validateFile(vf)
        }
      }

      override def onCreated(vf: VirtualFile): Unit = {
        validateFile(vf)
      }
    })
  }

   def validateFile(vf: VirtualFile): Unit = {
    _validationService.triggerValidation(vf.url(), () => validateDependencies(vf))
  }

  projectDefinition.registerListener(ProjectDefinition.LANGUAGE_LEVEL_PROP_NAME,
    (_: PropertyChangeEvent) => {
      validateAllEditors()
    }
  )

  private def validateAllEditors(): Unit = {
    documentService().openEditors().foreach((oe) => {
      validationService().triggerValidation(oe.file.url())
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
          logger.log(Level.WARNING,"No resource found for file " + vf.url())
        }
      }
    })
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

  def languageClient(): LanguageClient = {
    _client
  }

}
