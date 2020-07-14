package org.mule.weave.lsp.vfs

import java.io.File

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

class LSPFileBasedVirtualFile(fs: VirtualFileSystem, file: File) extends VirtualFile {
  override def fs(): VirtualFileSystem = fs

  override def read(): String = ???

  override def write(content: String): Unit = ???

  override def readOnly(): Boolean = ???

  override def path(): String = ???
}
