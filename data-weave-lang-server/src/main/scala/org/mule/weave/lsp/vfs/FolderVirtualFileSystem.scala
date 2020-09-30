package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URL
import java.net.URLDecoder

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.io.Source
import scala.util.Try

/**
 * This virtual file system is loaded from a folder
 *
 * @param folder The folder where this virtual file system is beeing loaded from
 */
class FolderVirtualFileSystem(folder: File) extends ReadOnlyVirtualFileSystem {

  override def file(path: String): VirtualFile = {
    val maybeFilePath = Try(new URL(path).getFile).toOption
    if (maybeFilePath.isEmpty) {
      null
    } else {
      val theFile = new File(URLDecoder.decode(maybeFilePath.get, "UTF-8"))
      if (theFile.exists()) {
        var content: String = ""
        val source = Source.fromFile(theFile, "UTF-8")
        try {
          content = source.mkString
        } finally {
          source.close()
        }
        new FileVirtualFile(theFile, this, content, folder)
      } else {
        null
      }
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new FolderWeaveResourceResolver(folder, this)
  }
}
