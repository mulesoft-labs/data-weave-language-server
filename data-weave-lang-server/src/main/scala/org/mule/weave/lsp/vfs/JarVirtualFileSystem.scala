package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.ReadOnlyVirtualFile
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipFile
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

/**
 * Represents a file system based on a JAR
 *
 * @param jarFile The Jar that backups this file
 */
class JarVirtualFileSystem(jarFile: File) extends ReadOnlyVirtualFileSystem with AutoCloseable {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  lazy val zipFile = new ZipFile(jarFile)

  override def file(path: String): VirtualFile = {
    logger.log(Level.INFO, s"file $path in ${jarFile.getAbsolutePath}")
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
            logger.log(Level.INFO, s"Error while trying to read `${path}` in ${jarFile.getAbsolutePath} : ${e.getMessage}")
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


  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    val entries = zipFile.entries()
    val result = new ArrayBuffer[VirtualFile]()
    while (entries.hasMoreElements) {
      val zipEntry = entries.nextElement()
      if (!zipEntry.isDirectory && zipEntry.getName.contains(filter)) {
        val source = Source.fromInputStream(zipFile.getInputStream(zipEntry), "UTF-8")
        try {
          result.+=(new ReadOnlyVirtualFile(zipEntry.getName, source.mkString, this))
        } finally {
          source.close()
        }
      }
    }
    result.toArray
  }

  override def asResourceResolver: WeaveResourceResolver = super.asResourceResolver

  override def close(): Unit = zipFile.close()
}
