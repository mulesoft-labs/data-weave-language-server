package org.mule.weave.lsp.vfs

import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.VFUtils
import org.mule.weave.lsp.vfs.resource.FolderWeaveResourceResolver
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.util
import scala.collection.JavaConverters.asJavaIteratorConverter

/**
  * This virtual file system is loaded from a folder
  *
  * @param folder The folder where this virtual file system is beeing loaded from
  */
class FolderVirtualFileSystem(override val artifactId: String, folder: File) extends ReadOnlyVirtualFileSystem with ArtifactVirtualFileSystem {

  override def file(path: String): VirtualFile = {
    val theFile: Option[File] = URLUtils.toFile(path)
    if (theFile.isEmpty) {
      null
    } else {
      if (theFile.get.exists()) {
        new FileVirtualFile(theFile.get, this, folder)
      } else {
        null
      }
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new FolderWeaveResourceResolver(folder, this)
  }

  override def listFiles(): util.Iterator[VirtualFile] = {
    VFUtils.listFiles(folder, this).asJava
  }

}
