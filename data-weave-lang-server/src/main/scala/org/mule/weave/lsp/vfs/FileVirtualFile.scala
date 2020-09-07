package org.mule.weave.lsp.vfs

import java.io.File

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.NameIdentifierHelper

import scala.io.Source

class FileVirtualFile(file: File, fs: VirtualFileSystem, path: String, rootFolder: File) extends VirtualFile {
  override def fs(): VirtualFileSystem = fs

  override def read(): String = {
    val source = Source.fromFile(file, "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  override def write(content: String): Unit = {}

  override def readOnly(): Boolean = true

  override def path(): String = {
    path
  }

  override def getNameIdentifier: NameIdentifier = {
    NameIdentifierHelper.fromWeaveFilePath(rootFolder.toPath.relativize(file.toPath).toString)
  }
}