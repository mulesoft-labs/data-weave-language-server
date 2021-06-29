package org.mule.weave.lsp.services

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.extension.client
import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.extension.client.ShowScenariosParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveScenario
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.Scenario
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
import org.mule.weave.v2.editor.VirtualFile

import java.util
import java.util.Collections
import scala.collection.JavaConverters.seqAsJavaListConverter


class PreviewService(agentService: WeaveAgentService, weaveLanguageClient: WeaveLanguageClient, project: Project) extends ToolingService {

  private var eventBus: EventBus = _

  @volatile
  private var enableValue: Boolean = false
  private var pendingProjectStart: Option[VirtualFile] = None
  private var currentVfPreview: Option[VirtualFile] = None

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

      def mapScenarios(maybeActiveScenario: Option[Scenario], allScenarios: Array[Scenario]): util.List[client.WeaveScenario] = {
        var resultScenarios = allScenarios
        val maybeScenario: Option[WeaveScenario] = maybeActiveScenario.map(activeScenario => {
          resultScenarios = allScenarios.filter(scenario => !scenario.name.equals(activeScenario.name))
          WeaveScenario(true, activeScenario.name, URLUtils.toLSPUrl(activeScenario.file), activeScenario.inputs().listFiles().map(file => URLUtils.toLSPUrl(file)).toList asJava, activeScenario.expected().map(_.listFiles().map(file => URLUtils.toLSPUrl(file)).toList asJava).orNull)
        })
        var scenarios: Array[WeaveScenario] = resultScenarios.map(scenario => {
          WeaveScenario(true, scenario.name, URLUtils.toLSPUrl(scenario.file), scenario.inputs().listFiles().map(file => URLUtils.toLSPUrl(file)).toList asJava, scenario.expected().map(_.listFiles().map(file => URLUtils.toLSPUrl(file)).toList asJava).orNull)
        })
        maybeScenario.map(s => scenarios = (scenarios :+ s))
        scenarios.toList asJava
      }

      override def onDocumentFocused(vf: VirtualFile): Unit = {
        if (enableValue) {
          runPreview(vf)
          //TODO move this from here
          projectKind.sampleDataManager().map(sampleManager => {
            val maybeActiveScenario = sampleManager.activeScenario(vf.getNameIdentifier)
            val allScenarios = sampleManager.listScenarios(vf.getNameIdentifier)
            val url = vf.url()
            URLUtils.toFile(url).map(file => weaveLanguageClient.showScenarios(scenariosParam = ShowScenariosParams(transformationUri = URLUtils.toLSPUrl(file), scenarios = mapScenarios(maybeActiveScenario, allScenarios))))
          })
        }
      }
    })

    eventBus.register(FILE_CHANGED_EVENT, new OnFileChanged {
      override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
        currentVfPreview.map(currentVFPreview => {
          projectKind.sampleDataManager().map(sampleDataManager => {
            sampleDataManager.searchSampleDataFolderFor(currentVFPreview.getNameIdentifier).map(scenarioFolder => {
              if (URLUtils.isChildOf(uri, scenarioFolder)) {
                runPreview(currentVFPreview)
              }
            })
          })
        })
      }
    })

    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
      override def onProjectStarted(project: Project): Unit = {
        if (enableValue && pendingProjectStart.isDefined) {
          runPreview(pendingProjectStart.get)
          pendingProjectStart = None
        }
      }
    })
  }

  def runPreview(vf: VirtualFile): Unit = {
    //If is the Preview scheme then we should ignore it
    if (!URLUtils.isSupportedEditableScheme(vf.url())) {
      return
    }
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
          null
        })
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
