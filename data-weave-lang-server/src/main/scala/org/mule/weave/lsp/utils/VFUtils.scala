package org.mule.weave.lsp.utils

import org.mule.weave.lsp.vfs.FileVirtualFile
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem

import java.io.File
import java.nio.file.Files
import java.util.stream
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * Helper class to work with Virtual File System
  */
object VFUtils {

  val DWL_EXTENSION =  ".dwl"

  def listFiles(folder: File, virtualFileSystem: VirtualFileSystem): Iterator[VirtualFile] = {
    if (!folder.exists()) {
      Iterator.empty
    } else {
      val value: stream.Stream[VirtualFile] = Files.walk(folder.toPath)
        .filter((f) => f.toFile.isFile)
        .map((f) => new FileVirtualFile(f.toFile, virtualFileSystem, folder))
      value
        .iterator().asScala
    }
  }
}
