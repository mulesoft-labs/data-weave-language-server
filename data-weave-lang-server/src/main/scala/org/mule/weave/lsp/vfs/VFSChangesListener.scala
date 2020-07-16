package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.VirtualFile

trait VFSChangesListener {

  def onDeleted(vf: VirtualFile): Unit = {}

  def onChanged(vf: VirtualFile): Unit = {}

  def onCreated(vf: VirtualFile): Unit = {}

}
