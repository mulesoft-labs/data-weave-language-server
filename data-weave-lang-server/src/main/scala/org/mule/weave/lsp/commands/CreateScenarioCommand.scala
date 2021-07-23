package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class CreateScenarioCommand(weaveLanguageClient: WeaveLanguageClient, scenariosManager: WeaveScenarioManagerService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_CREATE_SCENARIO

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier: String = Commands.argAsString(params.getArguments, 0)
    val nameOfTheScenario: WeaveInputBoxResult = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(value = "default", title = "Specify the name of the Scenario")).get()
    if (!nameOfTheScenario.cancelled) {
      val sampleName = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(title = "Specify The Sample Name", value = "payload.json", prompt = "Name of sample i.e payload.json or vars.foo.csv")).get()
      if (!sampleName.cancelled) {
        val maybeFile = scenariosManager.createScenario(NameIdentifier(nameIdentifier), nameOfTheScenario.value, sampleName.value)
        if (maybeFile.isDefined) {
          weaveLanguageClient.openTextDocument(OpenTextDocumentParams(toLSPUrl(maybeFile.get)))
        }
      }
    }
    null
  }
}
