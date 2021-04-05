package org.mule.weave.lsp.vfs

import java.io.File
import java.util.zip.ZipFile
import org.mule.weave.v2.editor.ReadOnlyVirtualFile
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.IOException
import scala.io.Source

class JarVirtualFileSystem(jarFile: File) extends ReadOnlyVirtualFileSystem with AutoCloseable {

  lazy val zipFile = new ZipFile(jarFile)

  override def file(path: String): VirtualFile = {
    println(s"[JarVirtualFileSystem] file $path in ${jarFile.getAbsolutePath}")
    val zipEntryPath = if (path.startsWith("/")) {
      path.substring(1)
    } else {
      path
    }
    val zipEntry = zipFile.getEntry(zipEntryPath)
    Option(zipEntry) match {
      case Some(entry) => {
        val source = Source.fromInputStream(zipFile.getInputStream(entry), "UTF-8")
        try {
          new ReadOnlyVirtualFile(path, source.mkString, this)
        } catch {
          case e: IOException => {
            println(s"[JarVirtualFileSystem] Error while trying to read `${path}` in ${jarFile.getAbsolutePath} : ${e.getMessage}")
            e.printStackTrace()
            null
          }
        }
        finally {
          source.close()
        }
      }
      case None => null
    }
  }

  override def asResourceResolver: WeaveResourceResolver = super.asResourceResolver

  override def close(): Unit = zipFile.close()
}
