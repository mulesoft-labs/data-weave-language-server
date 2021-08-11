package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class DeleteOutputSampleCommand(scenariosManager: WeaveScenarioManagerService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_DELETE_EXPECTED_OUTPUT

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier: String = Commands.argAsString(params.getArguments, 0)
    val nameOfScenario: String = Commands.argAsString(params.getArguments, 1)
    val outputUrl: String = Commands.argAsString(params.getArguments, 2)
    scenariosManager.deleteOutput(NameIdentifier(nameIdentifier), nameOfScenario, outputUrl)
    null
  }
}
