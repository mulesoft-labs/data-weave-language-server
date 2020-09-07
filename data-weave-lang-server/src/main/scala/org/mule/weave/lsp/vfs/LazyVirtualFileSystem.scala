package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.sdk.WeaveResourceResolver

class LazyVirtualFileSystem(delegate: () => VirtualFileSystem) extends VirtualFileSystem {

  private lazy val vfs: VirtualFileSystem = delegate()

  override def file(path: String): VirtualFile = {
    println(s"[LazyVirtualFileSystem] file ${path}")
    vfs.file(path)
  }

  override def removeChangeListener(listener: ChangeListener): Unit = vfs.removeChangeListener(listener)

  override def changeListener(cl: ChangeListener): Unit = vfs.changeListener(cl)

  override def onChanged(vf: VirtualFile): Unit = vfs.onChanged(vf)

  override def asResourceResolver: WeaveResourceResolver = vfs.asResourceResolver

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = vfs.listFilesByNameIdentifier(filter)
}