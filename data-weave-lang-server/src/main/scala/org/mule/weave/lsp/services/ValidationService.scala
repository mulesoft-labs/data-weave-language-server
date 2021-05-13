package org.mule.weave.lsp.services


import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.Settings
import org.mule.weave.lsp.project.events.OnProjectInitialized
import org.mule.weave.lsp.project.events.OnSettingsChanged
import org.mule.weave.lsp.project.events.ProjectInitializedEvent
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.LSPConverters.toDiagnostic
import org.mule.weave.lsp.utils.LSPConverters.toDiagnosticKind
import org.mule.weave.lsp.vfs.events.LibrariesModifiedEvent
import org.mule.weave.lsp.vfs.events.OnLibrariesModified
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.QuickFix
import org.mule.weave.v2.editor.ValidationMessage
import org.mule.weave.v2.editor.ValidationMessages
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.versioncheck.SVersion

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger

class ValidationService(project: Project, eventBus: EventBus, languageClient: LanguageClient, vfs: VirtualFileSystem, documentServiceFactory: () => WeaveToolingService, executor: Executor) {

  private val logger: Logger = Logger.getLogger(getClass.getName)


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

  eventBus.register(ProjectInitializedEvent.PROJECT_INITIALIZED, new OnProjectInitialized {
    override def onProjectInitialized(project: Project): Unit = {
      validateAllEditors()
    }
  })

  eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
    override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
      if (modifiedSettingsName.contains(Settings.LANGUAGE_LEVEL_PROP_NAME)) {
        validateAllEditors()
      }
    }
  })

  eventBus.register(LibrariesModifiedEvent.LIBRARIES_MODIFIED, new OnLibrariesModified {
    override def onLibrariesModified(): Unit = {
      validateAllEditors()
    }
  })


  private def validateAllEditors(): Unit = {
    documentService().openEditors().foreach((oe) => {
      triggerValidation(oe.file.url())
    })
  }


  private def validateDependencies(vf: VirtualFile): Unit = {
    val fileLogicalName: NameIdentifier = vf.getNameIdentifier
    val dependants: Seq[NameIdentifier] = documentService().dependantsOf(fileLogicalName)
    dependants.foreach((ni) => {
      vf.fs().asResourceResolver.resolve(ni) match {
        case Some(resource) => {
          triggerValidation(resource.url())
        }
        case None => {
          logger.log(Level.WARNING, "No resource found for file " + vf.url())
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


  def validateFile(vf: VirtualFile): Unit = {
    triggerValidation(vf.url(), () => validateDependencies(vf))
  }

  /**
    * Triggers the validation of the specified document.
    *
    * @param documentUri        The URI to be validated
    * @param onValidationFinish A Callback that is called when the validation finishes
    */
  def triggerValidation(documentUri: String, onValidationFinish: () => Unit = () => {}): Unit = {
    logger.log(Level.INFO, "triggerValidation of: " + documentUri)
    CompletableFuture.runAsync(() => {


      val diagnostics = new util.ArrayList[Diagnostic]
      withLanguageLevel(project.settings.languageLevelVersion.value())

      val messages: ValidationMessages = validate(documentUri)
      messages.errorMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Error))
      })

      messages.warningMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Warning))
      })

      languageClient().publishDiagnostics(new PublishDiagnosticsParams(documentUri, diagnostics))
      onValidationFinish()

    }, executor)
  }

  def quickFixesFor(documentUri: String, startOffset: Int, endOffset: Int, kind: String, severity: String): Array[QuickFix] = {
    val messages: ValidationMessages = validate(documentUri)
    val messageFound: Option[ValidationMessage] = if (severity == DiagnosticSeverity.Error.name()) {
      messages.errorMessage.find((m) => {
        matchesMessage(m, kind, startOffset, endOffset)
      })
    } else {
      messages.warningMessage.find((m) => {
        matchesMessage(m, kind, startOffset, endOffset)
      })
    }
    messageFound.map(_.quickFix).getOrElse(Array.empty)

  }


  private def matchesMessage(m: ValidationMessage, kind: String, startOffset: Int, endOffset: Int): Boolean = {
    m.location.startPosition.index == startOffset && m.location.endPosition.index == endOffset && toDiagnosticKind(m) == kind
  }

  /**
    * Executes Weave Validation into the corresponding type level
    *
    * @param documentUri The URI to be validates
    * @return The Validation Messages
    */
  def validate(documentUri: String): ValidationMessages = {
    val messages: ValidationMessages =
      if (project.initialized() && Settings.isTypeLevel(project.settings)) {
        openDocument(documentUri).typeCheck()
      } else if (project.initialized() && Settings.isScopeLevel(project.settings)) {
        openDocument(documentUri).scopeCheck()
      } else {
        openDocument(documentUri).parseCheck()
      }
    messages
  }

  def languageClient(): LanguageClient = {
    languageClient
  }

}
