package org.mule.weave.lsp.ui.utils

import org.mule.weave.lsp.extension.client.ThemeIcon
import org.mule.weave.lsp.extension.client.ThemeIconPath

object Buttons {

  case class Button(id: String, icon: ThemeIconPath)

  def back(): Button = {
    Button("back", ThemeIcon(id = "arrow-left"));
  }
}
