package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class CreateInputSampleCommand(weaveLanguageClient: WeaveLanguageClient, scenariosManager: WeaveScenarioManagerService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_CREATE_INPUT_SAMPLE

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier: String = Commands.argAsString(params.getArguments, 0)
    val nameOfScenario: String = Commands.argAsString(params.getArguments, 1)
    val sampleName = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(title = "Specify The Sample Name", value = "payload.json", prompt = "Name of sample i.e payload.json or vars.foo.csv")).get()
    if (!sampleName.cancelled) {
      val maybeFile = scenariosManager.createInput(NameIdentifier(nameIdentifier), nameOfScenario, sampleName.value)
      if (maybeFile.isDefined) {
        weaveLanguageClient.openTextDocument(OpenTextDocumentParams(toLSPUrl(maybeFile.get)))
      }
    }
    null
  }
}
