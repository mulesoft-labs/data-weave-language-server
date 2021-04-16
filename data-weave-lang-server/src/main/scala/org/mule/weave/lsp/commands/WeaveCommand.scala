package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams

trait WeaveCommand {

  def commandId(): String

  def execute(params: ExecuteCommandParams): AnyRef

}










