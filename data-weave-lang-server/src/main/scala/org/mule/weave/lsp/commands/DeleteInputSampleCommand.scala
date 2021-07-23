package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

class DeleteInputSampleCommand(scenariosManager: WeaveScenarioManagerService) extends WeaveCommand {

  override def commandId(): String = Commands.DW_DELETE_INPUT_SAMPLE

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val nameIdentifier: String = Commands.argAsString(params.getArguments, 0)
    val nameOfScenario: String = Commands.argAsString(params.getArguments, 1)
    val inputName: String = Commands.argAsString(params.getArguments, 2)
    scenariosManager.deleteInput(NameIdentifier(nameIdentifier), nameOfScenario, inputName)
    null
  }
}
