package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File

class CreateSampleData(projectKind: ProjectKind, weaveLanguageClient: WeaveLanguageClient) extends WeaveCommand {

  override def commandId(): String = Commands.DW_DEFINE_SAMPLE_DATA

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier = Commands.argAsString(params.getArguments, 0)
    val maybeSampleDataManager = projectKind.sampleDataManager()
    if(maybeSampleDataManager.isDefined){
      val sampleDataManager = maybeSampleDataManager.get
      val nameOfTheScenario = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams("default", "Specify the name of the Scenario")).get()
      if(!nameOfTheScenario.cancelled){
        val sampleContainer: File = sampleDataManager.createSampleDataFolderFor(NameIdentifier(nameIdentifier))
        val scenario: File = new File(sampleContainer, nameOfTheScenario.value.trim.replaceAll("\\s", "_"))
        val inputs: File = new File(scenario, "inputs")
        inputs.mkdirs()
        scenario.mkdirs()
      }
    }
    null
  }
}
