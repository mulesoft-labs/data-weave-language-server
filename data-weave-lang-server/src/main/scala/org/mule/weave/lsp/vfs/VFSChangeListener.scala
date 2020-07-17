package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile

trait VFSChangeListener extends ChangeListener{

  def onDeleted(vf: VirtualFile): Unit = {}

  def onChanged(vf: VirtualFile): Unit = {}

  def onCreated(vf: VirtualFile): Unit = {}

}
