package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

class LSPWorkspaceVirtualFileSystem(root: String) extends VirtualFileSystem {
  override def removeChangeListener(listener: ChangeListener): Unit = ???

  override def file(path: String): VirtualFile = ???

  override def changeListener(cl: ChangeListener): Unit = ???

  override def onChanged(vf: VirtualFile): Unit = ???
}
