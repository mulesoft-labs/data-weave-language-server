package org.mule.weave.lsp.utils

import org.eclipse.lsp4j.InitializeParams

import java.io.File
import java.net.URL

object RootFolderUtils {

  /**
   * This function has the logic to guess what is the root folder of a given project
   *
   * @param params The project information provided by the client
   * @return The root folder if it was inferred
   */
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
