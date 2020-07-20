package org.mule.weave.lsp.utils

import java.io.File
import java.net.URL

import org.eclipse.lsp4j.InitializeParams

object RootFolderUtils {
  def getRootFolder(params: InitializeParams): Option[File] = {
    Option(params.getRootUri)
      .orElse(Option(params.getRootUri))
      .map((url) => {
        new File(new URL(url).getFile)
      })
  }
}
