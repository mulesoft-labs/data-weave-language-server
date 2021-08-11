package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.util.logging.Logger

class SaveOutputCommand(weaveLanguageClient: WeaveLanguageClient, scenariosManager: WeaveScenarioManagerService, virtualFile: VirtualFileSystem, previewService: PreviewService) extends WeaveCommand {
  private val logger = Logger.getLogger(getClass.getName)
  override def commandId(): String = Commands.DW_SAVE_OUTPUT

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val uri = argAsString(params.getArguments, 0)
    val file: VirtualFile = virtualFile.file(uri)
    val transformId = file.getNameIdentifier
    val maybeScenario = scenariosManager.activeScenario(transformId)
    if (maybeScenario.isEmpty) {
      logger.info("No scenario for: " + transformId.name)
      return null
    }
    if (!previewService.canRunPreview(file.url())) {
      return null
    }

    val nameOfScenario = maybeScenario.get.name
    val sampleName = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(title = "Specify the expected output name", value = "out.json", prompt = "Name of the expected output i.e out.json")).get()
    if (!sampleName.cancelled) {
      val result = previewService.getPreviewResult(file)
      if (result.success) {
        scenariosManager.saveOutput(transformId, nameOfScenario, sampleName.value, result.content)
      } else {
        logger.info("Can't save output, there was an error running the current mapping.")
      }
    }
    null
  }
}
