package org.mule.weave.lsp.services

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.DeleteFile
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
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

  /**
    * The active scenario for each mapping
    */
  var activeScenarios: mutable.HashMap[NameIdentifier, Scenario] = mutable.HashMap()

  def mapScenarios(maybeActiveScenario: Option[Scenario], allScenarios: Array[Scenario]): util.List[client.WeaveScenario] = {
    val defaultScenarioName = maybeActiveScenario.map(_.name).getOrElse("")
    val scenarios: Array[WeaveScenario] = allScenarios.map(scenario => {
      val inputsList: Array[SampleInput] = scenario.inputs()
      val expectedOrNull: String = scenario.expected().map((file) => URLUtils.toLSPUrl(file)).orNull
      WeaveScenario(scenario.name.equals(defaultScenarioName), scenario.name, URLUtils.toLSPUrl(scenario.file), inputsList, expectedOrNull)
    })
    scenarios.toList.asJava
  }

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    eventBus.register(DocumentFocusChangedEvent.DOCUMENT_FOCUS_CHANGED, new OnDocumentFocused {

      override def onDocumentFocused(vf: VirtualFile): Unit = {
        if (URLUtils.isSupportedEditableScheme(vf.url())) {
          notifyAllScenarios(vf)
        }
      }
    })

    eventBus.register(DocumentOpenedEvent.DOCUMENT_OPENED, new OnDocumentOpened {
      override def onDocumentOpened(vf: VirtualFile): Unit = {
        if (URLUtils.isSupportedEditableScheme(vf.url())) {
          notifyAllScenarios(vf)
        }
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
    val maybeScenario = projectKind.sampleDataManager()
      .listScenarios(nameIdentifier)
      .find((scenario) => {
        scenario.name.equals(nameOfTheScenario)
      })

    maybeScenario.foreach((s) => {
      activeScenarios.put(nameIdentifier, s)
      notifyAllScenarios(nameIdentifier)
    })
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
    val maybeScenario = projectKind.sampleDataManager().searchScenarioByName(nameIdentifier, nameOfTheScenario)
    val maybeFile = maybeScenario
      .flatMap((scenario) => {
        doCreateInput(nameIdentifier, inputName, scenario.file)
      })
    maybeFile

  }

  def saveOutput(nameIdentifier: NameIdentifier, nameOfTheScenario: String, outputName: String, newContent: String): Option[File] = {
    val maybeScenario = projectKind.sampleDataManager().searchScenarioByName(nameIdentifier, nameOfTheScenario)
    val maybeFile = maybeScenario
      .flatMap((scenario) => {
        doSaveOutput(nameIdentifier, outputName, scenario.file, newContent)
      })
    maybeFile

  }

  def deleteOutput(nameIdentifier: NameIdentifier, nameOfTheScenario: String, outputUrl: String): Unit = {
    projectKind.sampleDataManager().searchScenarioByName(nameIdentifier, nameOfTheScenario)
      .foreach((_) => {
        URLUtils.toFile(outputUrl)
          .foreach((f) => f.delete())
      })

    notifyAllScenarios(nameIdentifier)
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

  private def doCreateInput(nameIdentifier: NameIdentifier, inputName: String, scenario: File): Option[File] = {
    val inputFile: File = inputOf(scenario, inputName)
    val response = applyEdits(createFileCmd(inputFile))
    notifyAllScenarios(nameIdentifier)
    if (response.isApplied) {
      Some(inputFile)
    } else {
      None
    }
  }

  private def deleteFileCmd(destinationFile: File): Either[TextDocumentEdit, ResourceOperation] = {
    val destinationFileUrl = toLSPUrl(destinationFile)
    val createFile = Either.forRight[TextDocumentEdit, ResourceOperation](new DeleteFile(destinationFileUrl))
    createFile
  }

  private def createFileCmd(destinationFile: File): Either[TextDocumentEdit, ResourceOperation] = {
    val destinationFileUrl = toLSPUrl(destinationFile)
    val createFile = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(destinationFileUrl))
    createFile
  }

  private def editFileCmd(destinationFile: File, startPos: Position, endPos: Position, newText: String): Either[TextDocumentEdit, ResourceOperation] = {
    val outputFileUrl = toLSPUrl(destinationFile)
    val textEdit = new TextEdit(new org.eclipse.lsp4j.Range(startPos, endPos), newText)
    val textDocumentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(outputFileUrl, 0), util.Arrays.asList(textEdit))
    val editFile = Either.forLeft[TextDocumentEdit, ResourceOperation](textDocumentEdit)
    editFile
  }

  private def applyEdits(edits: Either[TextDocumentEdit, ResourceOperation]*) = {
    val editsList = util.Arrays.asList(edits:_*)
    weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(editsList))).get()
  }

  private def doSaveOutput(nameIdentifier: NameIdentifier, outputName: String, scenario: File, newText: String): Option[File] = {
    val outputFile: File = outputOf(scenario, outputName)

    val docStart = new Position(0, 0)
    if (outputFile.exists()) {
      val result = applyEdits(deleteFileCmd(outputFile))
      if (!result.isApplied) {
        return None
      }
    }
    val response = applyEdits(
      createFileCmd(outputFile),
      editFileCmd(outputFile, docStart, docStart, newText)
    )
    if (response.isApplied) {
      notifyAllScenarios(nameIdentifier)
      Some(outputFile)
    } else {
      None
    }
  }


  /**
    * Gets the output file of a scenario
    */
  private def outputOf(scenario: File, outputName: String): File = {
    new File(scenario, outputName)
  }

  /**
    * Gets the input file of a scenario
    */
  private def inputOf(scenario: File, inputName: String): File = {
    val inputs: File = new File(scenario, "inputs")
    new File(inputs, getVariablePath(inputName))
  }

  /**
    * This method allows supporting 'vars.varName'
    */
  private def getVariablePath(fileName: String): String = {
    val theBaseName: String = FilenameUtils.getBaseName(fileName)
    val extension: String = FilenameUtils.getExtension(fileName)
    val variablePath: String = theBaseName.replace('.', File.separatorChar) + "." + extension
    variablePath
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
