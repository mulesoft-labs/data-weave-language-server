package org.mule.weave.lsp.utils

import org.mule.weave.v2.parser.ast.variables.NameIdentifier

object WTFUtils {

  val DWIT_FOLDER = "dwit"

  def toFolderName(nameIdentifier: NameIdentifier): String = {
    nameIdentifier.name.replaceAll(NameIdentifier.SEPARATOR, "-")
  }
}
