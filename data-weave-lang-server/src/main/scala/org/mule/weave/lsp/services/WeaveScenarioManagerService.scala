package org.mule.weave.lsp.services

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.mule.weave.lsp.extension.client
import org.mule.weave.lsp.extension.client.ShowScenariosParams
import org.mule.weave.lsp.extension.client.SampleInput
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveScenario
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.project.components.Scenario
import org.mule.weave.lsp.services.events.DocumentFocusChangedEvent
import org.mule.weave.lsp.services.events.DocumentOpenedEvent
import org.mule.weave.lsp.services.events.OnDocumentFocused
import org.mule.weave.lsp.services.events.OnDocumentOpened
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.LSPConverters
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable


class WeaveScenarioManagerService(weaveLanguageClient: WeaveLanguageClient, virtualFileSystem: VirtualFileSystem) extends ToolingService {

  var projectKind: ProjectKind = _

  var activeScenarios: mutable.HashMap[NameIdentifier, Scenario] = mutable.HashMap()

  def mapScenarios(maybeActiveScenario: Option[Scenario], allScenarios: Array[Scenario]): util.List[client.WeaveScenario] = {
    val defaultScenarioName = maybeActiveScenario.map(_.name).getOrElse("")
    val scenarios: Array[WeaveScenario] = allScenarios.map(scenario => {
      val files = FileUtils.listFiles(scenario.inputs(), null, true)
      val inputsList: Array[SampleInput] = files.iterator().asScala
        .map(file => {
          val relativePath = scenario.inputs().toPath.relativize(file.toPath)
          val inputName = relativePath.iterator().asScala.map((p) => FilenameUtils.getBaseName(p.toFile.getName)).mkString(".")
          SampleInput(URLUtils.toLSPUrl(file), inputName)
        }).toArray
      val expectedOrNull: String = scenario.expected().map((file) => URLUtils.toLSPUrl(file)).orNull
      WeaveScenario(scenario.name.equals(defaultScenarioName), scenario.name, URLUtils.toLSPUrl(scenario.file), inputsList, expectedOrNull)
    })
    scenarios.toList.asJava
  }

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    eventBus.register(DocumentFocusChangedEvent.DOCUMENT_FOCUS_CHANGED, new OnDocumentFocused {

      override def onDocumentFocused(vf: VirtualFile): Unit = {
        notifyAllScenarios(vf)
      }
    })

    eventBus.register(DocumentOpenedEvent.DOCUMENT_OPENED, new OnDocumentOpened {
      override def onDocumentOpened(vf: VirtualFile): Unit = {
        notifyAllScenarios(vf)
      }
    })
  }

  def activeScenario(nameIdentifier: NameIdentifier): Option[Scenario] = {
    activeScenarios.get(nameIdentifier)
      .orElse({
        val scenarios: Array[Scenario] = projectKind.sampleDataManager().listScenarios(nameIdentifier)
        if (scenarios != null && scenarios.nonEmpty) {
          val maybeFirstScenario: Option[Scenario] = scenarios.headOption
          maybeFirstScenario.foreach((scenario) => activeScenarios.put(nameIdentifier, scenario))
          maybeFirstScenario
        } else {
          None
        }
      })
  }

  def setActiveScenario(nameIdentifier: NameIdentifier, nameOfTheScenario: String): Unit = {
    projectKind.sampleDataManager()
      .listScenarios(nameIdentifier)
      .find((scenario) => {
        scenario.name.equals(nameOfTheScenario)
      })

    notifyAllScenarios(nameIdentifier)
  }

  def deleteScenario(nameIdentifier: NameIdentifier, nameOfTheScenario: String): Unit = {
    projectKind.sampleDataManager()
      .searchScenarioByName(nameIdentifier, nameOfTheScenario)
      .foreach((s) => {
        FileUtils.deleteDirectory(s.file)
      })
    notifyAllScenarios(nameIdentifier)
  }

  def createInput(nameIdentifier: NameIdentifier, nameOfTheScenario: String, inputName: String): Option[File] = {
    val maybeFile = projectKind.sampleDataManager().searchScenarioByName(nameIdentifier, nameOfTheScenario)
      .flatMap((scenario) => {
        doCreateInput(nameIdentifier, inputName, scenario.file)
      })
    maybeFile

  }

  def deleteInput(nameIdentifier: NameIdentifier, nameOfTheScenario: String, inputUrl: String): Unit = {
    projectKind.sampleDataManager().searchScenarioByName(nameIdentifier, nameOfTheScenario)
      .foreach((_) => {
        URLUtils.toFile(inputUrl)
          .foreach((f) => f.delete())
      })

    notifyAllScenarios(nameIdentifier)
  }

  def createScenario(nameIdentifier: NameIdentifier, nameOfTheScenario: String, inputName: String): Option[File] = {
    val sampleDataManager: SampleDataManager = projectKind.sampleDataManager()
    val sampleContainer: File = sampleDataManager.createSampleDataFolderFor(nameIdentifier)
    val scenario: File = new File(sampleContainer, nameOfTheScenario)
    doCreateInput(nameIdentifier, inputName, scenario)
  }

  private def doCreateInput(nameIdentifier: NameIdentifier, inputName: String, scenario: File) = {
    val inputFile: File = inputOf(scenario, inputName)
    val createFile: Either[TextDocumentEdit, ResourceOperation] = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(toLSPUrl(inputFile)))
    val edits: util.List[Either[TextDocumentEdit, ResourceOperation]] = util.Arrays.asList(createFile)
    val response = weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
    notifyAllScenarios(nameIdentifier)
    if (response.isApplied) {
      Some(inputFile)
    } else {
      None
    }
  }

  private def inputOf(scenario: File, inputName: String) = {
    val inputs: File = new File(scenario, "inputs")
    val theBaseName: String = FilenameUtils.getBaseName(inputName)
    val variablePath: String = theBaseName.replace('.', File.separatorChar) + "." + FilenameUtils.getExtension(inputName)
    val inputFile: File = new File(inputs, variablePath)
    inputFile
  }

  private def notifyAllScenarios(vf: VirtualFile): Unit = {
    notifyAllScenarios(vf.getNameIdentifier)
  }

  private def notifyAllScenarios(nameIdentifier: NameIdentifier): Unit = {
    val maybeActiveScenario: Option[Scenario] = activeScenario(nameIdentifier)
    val allScenarios = projectKind.sampleDataManager().listScenarios(nameIdentifier)
    weaveLanguageClient.showScenarios(
      scenariosParam = ShowScenariosParams(
        nameIdentifier = nameIdentifier.toString(),
        scenarios = mapScenarios(maybeActiveScenario, allScenarios)
      )
    )
  }
}
