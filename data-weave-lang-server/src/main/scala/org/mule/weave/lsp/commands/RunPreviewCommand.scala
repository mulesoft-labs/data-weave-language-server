package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

class RunPreviewCommand(previewService: PreviewService, virtualFile: VirtualFileSystem) extends WeaveCommand {
  override def commandId(): String = Commands.DW_RUN_PREVIEW

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val uri = argAsString(params.getArguments, 0)
    val file: VirtualFile = virtualFile.file(uri)
    if (file != null) {
      previewService.runPreview(file)
    }
    null
  }
}
