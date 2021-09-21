package org.mule.weave.lsp.services

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.jobs.Status
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.services.events.DocumentChangedEvent
import org.mule.weave.lsp.services.events.DocumentFocusChangedEvent
import org.mule.weave.lsp.services.events.DocumentOpenedEvent
import org.mule.weave.lsp.services.events.FileChangedEvent._
import org.mule.weave.lsp.services.events.OnDocumentChanged
import org.mule.weave.lsp.services.events.OnDocumentFocused
import org.mule.weave.lsp.services.events.OnDocumentOpened
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.WeaveASTQueryUtils
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.util.Collections
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.logging.Logger


class PreviewService(agentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, project: Project, toolingServices: DataWeaveToolingService, jobManager: JobManagerService) extends ToolingService {
  private val logger = Logger.getLogger(getClass.getName)
  private var eventBus: EventBus = _

  @volatile
  private var enableValue: Boolean = false
  private var pendingProjectStart: Option[VirtualFile] = None
  private var currentVfPreview: Option[VirtualFile] = None
  private val previewDebouncer = new Debouncer[NameIdentifier]

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.eventBus = eventBus
    eventBus.register(DocumentChangedEvent.DOCUMENT_CHANGED, new OnDocumentChanged {
      override def onDocumentChanged(vf: VirtualFile): Unit = {
        if (enableValue) {
          runPreview(vf)
        }
      }
    })

    eventBus.register(DocumentOpenedEvent.DOCUMENT_OPENED, new OnDocumentOpened {
      override def onDocumentOpened(vf: VirtualFile): Unit = {
        if (enableValue) {
          runPreview(vf)
        }
      }
    })

    eventBus.register(DocumentFocusChangedEvent.DOCUMENT_FOCUS_CHANGED, new OnDocumentFocused {
      override def onDocumentFocused(vf: VirtualFile): Unit = {
        if (enableValue) {
          runPreview(vf)
        }
      }
    })

    eventBus.register(FILE_CHANGED_EVENT, new OnFileChanged {
      override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
        currentVfPreview.map(currentVFPreview => {
          projectKind.sampleDataManager().searchSampleDataFolderFor(currentVFPreview.getNameIdentifier).map(scenarioFolder => {
            if (URLUtils.isChildOf(uri, scenarioFolder)) {
              runPreview(currentVFPreview)
            }
          })
        })
      }
    })

    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
      override def onProjectStarted(project: Project): Unit = {
        if (pendingProjectStart.isDefined) {
          runPreview(pendingProjectStart.get)
          pendingProjectStart = None
        }
      }
    })
  }

  def getPreviewResult(vf: VirtualFile): PreviewResult = {
    val fileUrl: String = vf.url()
    if (!project.isStarted()) {
      PreviewResult(
        uri = fileUrl,
        success = false,
        logs = Collections.emptyList(),
        errorMessage = "Project is not yet initialized.\nPreview is going to be executed once project initializes."
      )
    } else {
      val identifier: NameIdentifier = vf.getNameIdentifier
      val content: String = vf.read()
      logger.info(s"Trigger run preview for `${identifier}`.")
      agentService.run(identifier, content, fileUrl)
    }
  }

  def canRunPreview(fileUrl: String): Boolean = {
    val maybeAstNode = toolingServices.openDocument(fileUrl).ast()
    val isMapping = WeaveASTQueryUtils.fileKind(maybeAstNode)
      .forall((kind) => kind.equals(WeaveASTQueryUtils.MAPPING))

    if (!URLUtils.isSupportedEditableScheme(fileUrl) || !isMapping) {
      false
    } else {
      true
    }
  }

  def runPreview(vf: VirtualFile): Unit = {
    val fileUrl: String = vf.url()
    if (!canRunPreview(fileUrl)) {
      return
    }

    if (!project.isStarted()) {
      pendingProjectStart = Some(vf)
      weaveLanguageClient.showPreviewResult(getPreviewResult(vf))
    } else {
      val identifier: NameIdentifier = vf.getNameIdentifier
      //Debounce the changes for 300ms
      previewDebouncer.debounce(identifier, () => {
        jobManager.execute((status: Status) => {
          weaveLanguageClient.showPreviewResult(getPreviewResult(vf))
        }, "Running preview", s"Running preview of ${identifier}"
        )
      }, 300, TimeUnit.MILLISECONDS);
    }
    currentVfPreview = Some(vf)
  }

  def enable(): Unit = {
    this.enableValue = true
  }

  def disable(): Unit = {
    this.enableValue = false
    this.currentVfPreview = None
  }
}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class Debouncer[T] {
  final private val scheduler = Executors.newSingleThreadScheduledExecutor
  final private val delayedMap = new ConcurrentHashMap[T, Future[_]]
  private val logger = Logger.getLogger(getClass.getName)

  def debounce(key: T, runnable: Runnable, delay: Long, unit: TimeUnit): Unit = {
    val prev: Future[_] = delayedMap.put(key, scheduler.schedule(new Runnable() {
      override def run(): Unit = {
        try {
          runnable.run()
        }
        finally {
          delayedMap.remove(key)
        }
      }
    }, delay, unit))
    if (prev != null) {
      logger.info("Canceling previous execution.")
      prev.cancel(true)
    }
  }

  def shutdown(): Unit = {
    scheduler.shutdownNow
  }
}