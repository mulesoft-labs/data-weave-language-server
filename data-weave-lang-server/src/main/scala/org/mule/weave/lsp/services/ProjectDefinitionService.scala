package org.mule.weave.lsp.services

import java.io.File

import org.mule.weave.lsp.vfs.ClassloaderVirtualFileSystem
import org.mule.weave.v2.editor.VirtualFileSystem

trait ProjectDefinitionService {

  /**
   * The root folders of all the available sources
   *
   * @return
   */
  def sourceFolder(): Seq[File]

  /**
   * Returns the dependencies virtual file system that handles the project dependencies
   *
   * @return
   */
  def dependenciesVFS(): VirtualFileSystem

}


object EmptyProjectDefinitionService extends ProjectDefinitionService {

  override def sourceFolder(): Seq[File] = Seq()

  override def dependenciesVFS(): VirtualFileSystem = new ClassloaderVirtualFileSystem(this.getClass.getClassLoader)
}

class ProjectDefinitionServiceProxy(var proxy: ProjectDefinitionService = EmptyProjectDefinitionService) extends ProjectDefinitionService {

  override def sourceFolder(): Seq[File] = proxy.sourceFolder()

  override def dependenciesVFS(): VirtualFileSystem = proxy.dependenciesVFS()
}