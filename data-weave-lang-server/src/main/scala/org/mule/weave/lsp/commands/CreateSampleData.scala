package org.mule.weave.lsp.commands

import org.apache.commons.io.FilenameUtils
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages._
import org.mule.weave.lsp.client.OpenTextDocumentParams
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.SampleDataManager
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util

class CreateSampleData(projectKind: ProjectKind, weaveLanguageClient: WeaveLanguageClient) extends WeaveCommand {

  override def commandId(): String = Commands.DW_DEFINE_SAMPLE_DATA

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier = Commands.argAsString(params.getArguments, 0)
    val maybeSampleDataManager: Option[SampleDataManager] = projectKind.sampleDataManager()
    if (maybeSampleDataManager.isDefined) {
      val sampleDataManager = maybeSampleDataManager.get
      val nameOfTheScenario = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(value = "default", title = "Specify the name of the Scenario")).get()
      if (!nameOfTheScenario.cancelled) {
        val sampleName = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(title = "Specify The Sample Name", value = "payload.json", prompt = "Name of sample i.e payload.json or vars.foo.csv")).get()
        if (!sampleName.cancelled) {
          val sampleContainer: File = sampleDataManager.createSampleDataFolderFor(NameIdentifier(nameIdentifier))
          val scenario: File = new File(sampleContainer, nameOfTheScenario.value.trim.replaceAll("\\s", "_"))
          val inputs: File = new File(scenario, "inputs")
          val value: String = sampleName.value
          val theBaseName: String = FilenameUtils.getBaseName(value)
          val variablePath: String = theBaseName.replace('.', File.separatorChar) + "." + FilenameUtils.getExtension(value)
          val inputFile: File = new File(inputs, variablePath)
          val createFile: Either[TextDocumentEdit, ResourceOperation] = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(toLSPUrl(inputFile)))
          val edits: util.List[Either[TextDocumentEdit, ResourceOperation]] = util.Arrays.asList(createFile)
          val response = weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
          if (response.isApplied) {
            weaveLanguageClient.openTextDocument(OpenTextDocumentParams(toLSPUrl(inputFile)))
          }
        }
      }
    }
    null
  }
}
