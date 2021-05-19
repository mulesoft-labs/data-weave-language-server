package org.mule.weave.lsp.vfs

import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.net.URI
import java.util
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import scala.collection.JavaConverters.asJavaIteratorConverter
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.io.Source

/**
  * Represents a file system based on a JAR
  *
  * @param jarFile The Jar that backups this file
  */
class JarVirtualFileSystem(override val artifactId: String, val jarFile: File) extends ReadOnlyVirtualFileSystem with ArtifactVirtualFileSystem with AutoCloseable {

  private val logger: Logger = Logger.getLogger(getClass.getName)
  lazy val zipFile = new ZipFile(jarFile)

  override def file(path: String): VirtualFile = {
    URLUtils.toURI(path) match {
      case Some(uri) if (uri.getScheme == "jar" && jarFile.getCanonicalPath == jarPath(uri)) => {
        resolveLocalPath(jarEntry(uri))
      }
      case _ => {
        resolveLocalPath(path)
      }
    }
  }

  private def jarPath(uri: URI) = {
    val parts: Array[String] = jarUriParts(uri)
    parts(0)
  }

  private def jarEntry(uri: URI) = {
    val parts: Array[String] = jarUriParts(uri)
    parts(1)
  }

  private def jarUriParts(uri: URI) = {
    uri.getPath.split("!")
  }

  private def resolveLocalPath(path: String) = {
    logger.log(Level.INFO, s"file $path in ${jarFile.getAbsolutePath}")
    val zipEntryPath = if (path.startsWith("/")) {
      path.substring(1)
    } else {
      path
    }
    val zipEntry: ZipEntry = zipFile.getEntry(zipEntryPath)
    Option(zipEntry) match {
      case Some(entry) => {
        new JarVirtualFile(zipEntry.getName, entry, zipFile, this)
      }
      case None => null
    }
  }

  override def listFiles(): util.Iterator[VirtualFile] = {
    val entries = zipFile.entries().asScala
    val list: Iterator[VirtualFile] = entries.flatMap((zipEntry) => {
      if (zipEntry.isDirectory) {
        None
      } else {
        Some(new JarVirtualFile(zipEntry.getName, zipEntry, zipFile, this))
      }
    })
    list.asJava
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new JarVirtualFSResourceProvider(this)
  }

  override def close(): Unit = zipFile.close()
}

class JarVirtualFSResourceProvider(vfs: VirtualFileSystem) extends WeaveResourceResolver {

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path = NameIdentifierHelper.toWeaveFilePath(name)
    resolvePath(path)
  }

  override def resolvePath(path: String): Option[WeaveResource] = {
    val virtualFile: VirtualFile = vfs.file(path)
    Option(virtualFile).map((_) => {
      virtualFile.asResource()
    })
  }

  override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = {
    resolve(name).toSeq
  }

}


class JarVirtualFile(val entryJar: String, var entry: ZipEntry, zipFile: ZipFile, val fs: JarVirtualFileSystem) extends VirtualFile {

  private lazy val content: String = {
    val stream = zipFile.getInputStream(entry)
    val source = Source.fromInputStream(stream, "UTF-8")
    try {
      source.mkString
    } finally {
      stream.close()
    }
  }

  override def read(): String = {
    content
  }

  override def write(content: String): Boolean = {
    false
  }

  override def readOnly(): Boolean = true

  override def url(): String = {
    val jarUri = fs.jarFile.toURI
    val uri = new URI("jar", jarUri.getUserInfo, jarUri.getHost, jarUri.getPort, jarUri.getPath + "!/" + entryJar, jarUri.getQuery, jarUri.getFragment)
    uri.toString
  }

  override def asResource(): WeaveResource = {
    WeaveResource(url(), read())
  }

  override def getNameIdentifier: NameIdentifier = {
    NameIdentifierHelper.fromWeaveFilePath(entryJar)
  }

  override def path(): String = {
    "/" + entryJar
  }
}
