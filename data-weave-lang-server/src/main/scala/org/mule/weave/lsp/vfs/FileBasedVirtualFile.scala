package org.mule.weave.lsp.vfs

import java.io.File

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

import scala.io.Source

class FileBasedVirtualFile(fs: VirtualFileSystem, file: File) extends VirtualFile {
  override def fs(): VirtualFileSystem = fs

  override def read(): String = {
    val source = Source.fromFile(file, "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  override def write(content: String): Unit = {
    //
  }

  override def readOnly(): Boolean = {
    false
  }

  override def path(): String = {
    file.toURI.toURL.toString
  }
}
