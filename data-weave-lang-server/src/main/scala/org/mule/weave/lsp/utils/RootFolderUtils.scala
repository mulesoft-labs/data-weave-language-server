package org.mule.weave.lsp.utils

import java.io.File
import java.net.URL

import org.eclipse.lsp4j.InitializeParams

object RootFolderUtils {
  def getRootFolder(params: InitializeParams): Option[File] = {
    Option(params.getRootUri)
      .orElse(Option(params.getRootUri))
      .map((url) => {
        val rootProject = new File(new URL(url).getFile)
        val srcFolder = new File(rootProject, "src")
        if (srcFolder.exists()) {
          //If src folder exists then use that one
          srcFolder
        } else {
          rootProject
        }
      })
  }
}
