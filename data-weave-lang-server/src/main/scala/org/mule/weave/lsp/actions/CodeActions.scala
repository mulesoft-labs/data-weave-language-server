package org.mule.weave.lsp.actions

import org.eclipse.lsp4j.CodeActionParams
import org.mule.weave.lsp.services.DataWeaveToolingService

class CodeActions(weaveService: DataWeaveToolingService) {

  private val actions = Seq(
    new QuickFixAction(weaveService),
    new InsertDocumentationAction(weaveService),
    new InsertWeaveTypeAction(weaveService),
    new RefactorActionProvider(weaveService)
  )

  def actionsFor(params: CodeActionParams): Seq[CodeActionProvider] = {
    actions.filter(_.handles(params))
  }

}
