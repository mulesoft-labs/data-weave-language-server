package org.mule.weave.lsp.services

import org.mule.weave.lsp.client.PreviewResult
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.project.service.ToolingService
import org.mule.weave.lsp.project.service.WeaveAgentService
import org.mule.weave.lsp.services.events.DocumentChangedEvent
import org.mule.weave.lsp.services.events.DocumentOpenedEvent
import org.mule.weave.lsp.services.events.OnDocumentChanged
import org.mule.weave.lsp.services.events.OnDocumentOpened
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.v2.editor.VirtualFile

import java.util.Collections

class PreviewService(agentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, project: Project) extends ToolingService {

  private var eventBus: EventBus = _

  @volatile
  private var started: Boolean = false
  private var pendingProjectStart: Option[VirtualFile] = None

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.eventBus = eventBus
    eventBus.register(DocumentChangedEvent.DOCUMENT_CHANGED, new OnDocumentChanged {
      override def onDocumentChanged(vf: VirtualFile): Unit = {
        if (started) {
          runPreview(vf)
        }
      }
    })

    eventBus.register(DocumentOpenedEvent.DOCUMENT_OPENED, new OnDocumentOpened {
      override def onDocumentOpened(vf: VirtualFile): Unit = {
        if (started) {
          runPreview(vf)
        }
      }
    })

    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
      override def onProjectStarted(project: Project): Unit = {
        if (started && pendingProjectStart.isDefined) {
          runPreview(pendingProjectStart.get)
          pendingProjectStart = None
        }
      }
    })
  }

  def runPreview(vf: VirtualFile): Unit = {
    if (!project.isStarted()) {
      pendingProjectStart = Some(vf)
      weaveLanguageClient.showPreviewResult(
        PreviewResult(
          uri = vf.url(),
          success = false,
          logs = Collections.emptyList(),
          errorMessage = "Project is not yet initialized. Preview is going to be executed once project initializes.")
      )
    } else {
      agentService.run(vf.getNameIdentifier, vf.read(), vf.url())
        .thenApply((previewResult) => {
          weaveLanguageClient.showPreviewResult(previewResult)
          return null
        })
    }
  }

  override def start(): Unit = {
    started = true
  }

  override def stop(): Unit = {
    started = false
  }
}
