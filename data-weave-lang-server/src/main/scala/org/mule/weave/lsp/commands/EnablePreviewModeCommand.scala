package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.commands.Commands.argAsBoolean
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

class EnablePreviewModeCommand(previewService: PreviewService, virtualFile: VirtualFileSystem) extends WeaveCommand {
  override def commandId(): String = Commands.DW_ENABLE_PREVIEW

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val enabled = argAsBoolean(params.getArguments, 0)
    if (enabled) {
      val uri = argAsString(params.getArguments, 1)
      previewService.start()
      val file: VirtualFile = virtualFile.file(uri)
      if (file != null) {
        previewService.runPreview(file)
      }
    } else {
      previewService.stop()
    }
    null
  }
}
