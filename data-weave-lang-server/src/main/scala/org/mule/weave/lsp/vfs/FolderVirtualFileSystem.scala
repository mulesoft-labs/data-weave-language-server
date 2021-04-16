package org.mule.weave.lsp.vfs

import org.apache.commons.io.FilenameUtils
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * This virtual file system is loaded from a folder
 *
 * @param folder The folder where this virtual file system is beeing loaded from
 */
class FolderVirtualFileSystem(folder: File) extends ReadOnlyVirtualFileSystem {

  override def file(path: String): VirtualFile = {
    val theFile: Option[File] = Try(Paths.get(new URL(path).toURI).toFile).toOption
    if (theFile.isEmpty) {
      null
    } else {
      if (theFile.get.exists()) {
        new FileVirtualFile(theFile.get, this, path, folder)
      } else {
        null
      }
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new FolderWeaveResourceResolver(folder, this)
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    val validFiles = new ArrayBuffer[File]()
    Files.walkFileTree(folder.toPath, Collections.emptySet(), 10, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (FilenameUtils.getExtension(file.toFile.getName) == "dwl") {
          validFiles.+=(file.toFile)
        }
        FileVisitResult.CONTINUE
      }
    })
    val files = validFiles.map((vf) => {
      new FileVirtualFile(vf, this, vf.toPath.toUri.toURL.toString, folder)
    })
    files.toArray
  }
}
