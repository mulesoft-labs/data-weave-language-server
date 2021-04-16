package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams

trait CodeActionProvider {

  def handles(action: CodeActionParams): Boolean

  def actions(action: CodeActionParams): Array[CodeAction]

}
