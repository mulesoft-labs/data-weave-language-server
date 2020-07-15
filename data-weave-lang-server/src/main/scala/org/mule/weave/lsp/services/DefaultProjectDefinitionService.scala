package org.mule.weave.lsp.services

import java.io.File
import java.net.URL

import org.eclipse.lsp4j.InitializeParams
import org.mule.weave.lsp.vfs.ClassloaderVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

class DefaultProjectDefinitionService(params: InitializeParams) extends ProjectDefinitionService {
  /**
   * The root folders of all the available sources
   *
   * @return
   */
  override def sourceFolder(): Seq[File] = {
    Option(params.getRootUri)
      .orElse(Option(params.getRootUri))
      .map((url) => {
        new File(new URL(url).getFile)
      }).toSeq
  }

  /**
   * Returns the dependencies virtual file system that handles the project dependencies
   *
   * @return
   */
  override def dependenciesVFS(): VirtualFileSystem = {
    new ClassloaderVirtualFileSystem(this.getClass.getClassLoader)
  }
}
