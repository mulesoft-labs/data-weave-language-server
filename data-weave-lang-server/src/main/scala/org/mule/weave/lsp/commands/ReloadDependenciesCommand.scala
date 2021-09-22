package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.commands.Commands.argAsBoolean
import org.mule.weave.lsp.commands.Commands.argAsString
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.PreviewService
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

class ReloadDependenciesCommand(project: ProjectKind) extends WeaveCommand {
  override def commandId(): String = Commands.DW_RELOAD_DEPENDENCIES

  override def execute(params: ExecuteCommandParams): AnyRef = {
    project.dependencyManager().reload()
    null
  }
}
