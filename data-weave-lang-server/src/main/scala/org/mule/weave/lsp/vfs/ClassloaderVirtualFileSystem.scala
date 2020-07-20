package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ReadOnlyVirtualFile
import org.mule.weave.v2.editor.VirtualFile

import scala.io.Source

class ClassloaderVirtualFileSystem(classLoader: ClassLoader, encoding: String = "UTF-8") extends ReadOnlyVirtualFileSystem {

  override def file(path: String): VirtualFile = {
    val sanitizedPath = if (path.startsWith("/")) {
      path.substring(1)
    } else {
      path
    }
    val pathStream = classLoader.getResource(sanitizedPath)
    Option(pathStream).map((is) => {
      val source = Source.fromInputStream(is.openStream(), encoding)
      try {
        new ReadOnlyVirtualFile(path, source.mkString, this)
      } finally {
        source.close()
      }
    }).orNull
  }
}
