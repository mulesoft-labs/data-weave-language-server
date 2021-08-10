package org.mule.weave.lsp.services


import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent
import org.mule.weave.lsp.indexer.events.OnIndexingFinished
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.Settings
import org.mule.weave.lsp.project.components.MetadataProvider
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.OnSettingsChanged
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.project.events.SettingsChangedEvent
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.LSPConverters.toDiagnostic
import org.mule.weave.lsp.utils.LSPConverters.toDiagnosticKind
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.URLUtils.isDWFile
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
import org.mule.weave.v2.ts.WeaveType
import org.mule.weave.v2.versioncheck.SVersion

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger

class DataWeaveToolingService(project: Project, languageClient: LanguageClient, vfs: VirtualFileSystem, documentServiceFactory: () => WeaveToolingService, executor: Executor) extends ToolingService {


  private val logger: Logger = Logger.getLogger(getClass.getName)
  private var projectKind: ProjectKind = _
  private lazy val _documentService: WeaveToolingService = documentServiceFactory()

  @volatile
  private var indexed: Boolean = false

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    vfs.changeListener(new ChangeListener {

      override def onDeleted(vf: VirtualFile): Unit = {
        if (isDWFile(vf.url())) {
          validateDependencies(vf, "onDeleted")
        } else {
          findCorrespondingDwFile(vf.url()).foreach(dwVirtualFile => validateFile(dwVirtualFile, "onScenarioChanged"))
        }
      }

      override def onChanged(vf: VirtualFile): Unit = {
        if (isDWFile(vf.url())) {
          validateFile(vf, "onChanged")
        } else {
          findCorrespondingDwFile(vf.url()).foreach(dwVirtualFile => validateFile(dwVirtualFile, "onScenarioChanged"))
        }
      }

      override def onCreated(vf: VirtualFile): Unit = {
        if (isDWFile(vf.url())) {
          validateFile(vf, "onCreated")
        } else {
          findCorrespondingDwFile(vf.url()).foreach(dwVirtualFile => validateFile(dwVirtualFile, "onScenarioChanged"))
        }
      }
    })

    eventBus.register(SettingsChangedEvent.SETTINGS_CHANGED, new OnSettingsChanged {
      override def onSettingsChanged(modifiedSettingsName: Array[String]): Unit = {
        if (modifiedSettingsName.contains(Settings.LANGUAGE_LEVEL_PROP_NAME)) {
          validateAllEditors("settingsChanged")
        }
      }
    })

    eventBus.register(IndexingFinishedEvent.INDEXING_FINISHED, new OnIndexingFinished() {
      override def onIndexingFinished(): Unit = {
        indexed = true
        validateAllEditors("indexingFinishes")
      }
    })

    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
      override def onProjectStarted(project: Project): Unit = {
        validateAllEditors("projectStarted")
      }
    })
  }

  private def findCorrespondingDwFile(str: String): Option[VirtualFile] = {
    val sampleDataManager = projectKind.sampleDataManager()
    documentService().openEditors().find((oe) => {
      URLUtils.isChildOfAny(str, sampleDataManager.listScenarios(oe.file.getNameIdentifier).map(scenario => scenario.file))
    }).map(weaveDocToolingService => weaveDocToolingService.file)
  }

  private def validateAllEditors(reason: String): Unit = {
    //Invalidate all caches
    documentService().invalidateAll()
    documentService().openEditors().foreach((oe) => {
      triggerValidation(oe.file.url(), reason)
    })
  }


  private def validateDependencies(vf: VirtualFile, reason: String): Unit = {
    val fileLogicalName: NameIdentifier = vf.getNameIdentifier
    val dependants: Seq[NameIdentifier] = documentService().dependantsOf(fileLogicalName)
    dependants.foreach((ni) => {
      vf.fs().asResourceResolver.resolve(ni) match {
        case Some(resource) => {
          triggerValidation(resource.url(), "dependantChanged ->" + reason + s" ${vf.url()}")
        }
        case None => {
          logger.log(Level.WARNING, "No resource found for file " + vf.url())
        }
      }
    })
  }

  def loadType(typeString: String): Option[WeaveType] = {
    documentService().loadType(typeString)
  }

  private def documentService(): WeaveToolingService = {
    _documentService
  }

  def openDocument(uri: String, withExpectedOutput: Boolean = true): WeaveDocumentToolingService = {
    val maybeProvider: Option[MetadataProvider] = projectKind.metadataProvider()
    val input = maybeProvider match {
      case Some(value) => {
        val virtualFile: VirtualFile = vfs.file(uri)
        if (virtualFile != null) {
          val inputMetadata = value.inputMetadataFor(virtualFile)
          ImplicitInput(inputMetadata.metadata.map((m) => (m.name, m.wtype)).toMap)
        } else {
          ImplicitInput()
        }
      }
      case None => ImplicitInput()
    }

    val expected = if (withExpectedOutput) {
      maybeProvider.flatMap((metadataProvider) => {
        val virtualFile: VirtualFile = vfs.file(uri)
        if (virtualFile != null) {
          metadataProvider.outputMetadataFor(virtualFile)
        } else {
          None
        }
      })
    } else {
      None
    }
    _documentService.open(uri, input, expected)
  }

  def closeDocument(uri: String): Unit = {
    documentService().close(uri)
  }

  def withLanguageLevel(dwLanguageLevel: String): WeaveToolingService = {
    _documentService.updateLanguageLevel(SVersion.fromString(dwLanguageLevel))
  }


  def validateFile(vf: VirtualFile, reason: String): Unit = {
    triggerValidation(vf.url(), reason, () => validateDependencies(vf, reason))
  }

  /**
    * Triggers the validation of the specified document.
    *
    * @param documentUri        The URI to be validated
    * @param onValidationFinish A Callback that is called when the validation finishes
    */
  def triggerValidation(documentUri: String, reason: String, onValidationFinish: () => Unit = () => {}): Unit = {
    logger.log(Level.INFO, "TriggerValidation of: " + documentUri + " reason " + reason)
    CompletableFuture.runAsync(() => {
      val diagnostics = new util.ArrayList[Diagnostic]
      withLanguageLevel(projectKind.dependencyManager().languageLevel())

      val messages: ValidationMessages = validate(documentUri)
      messages.errorMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Error))
      })

      messages.warningMessage.foreach((message) => {
        diagnostics.add(toDiagnostic(message, DiagnosticSeverity.Warning))
      })
      logger.log(Level.INFO, "TriggerValidation finished: " + documentUri + " reason " + reason)
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
      if (indexed && Settings.isTypeLevel(project.settings)) {
        openDocument(documentUri, withExpectedOutput = false).typeCheck()
      } else if (indexed && Settings.isScopeLevel(project.settings)) {
        openDocument(documentUri, withExpectedOutput = false).scopeCheck()
      } else {
        openDocument(documentUri, withExpectedOutput = false).parseCheck()
      }
    messages
  }

  def languageClient(): LanguageClient = {
    languageClient
  }

}
