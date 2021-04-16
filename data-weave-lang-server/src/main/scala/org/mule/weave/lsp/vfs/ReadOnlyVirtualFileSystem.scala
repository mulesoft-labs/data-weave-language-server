package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

/**
 * A Trait to mark a VFS as ReadOnly
 */
trait ReadOnlyVirtualFileSystem extends VirtualFileSystem {

  override def removeChangeListener(listener: ChangeListener): Unit = {}

  override def changeListener(cl: ChangeListener): Unit = {}

  override def onChanged(vf: VirtualFile): Unit = {}

}
