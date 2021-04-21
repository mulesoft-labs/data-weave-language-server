package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource

import java.io.File
import scala.io.Source

/**
 * This project represents the VirtualFile from the ProjectVirtualFileSystem
 *
 * @param fs          The file system that created this virtual file
 * @param url         The Url of this File
 * @param file        The underlying File if any. It may be absent if the file hasn't been persisted yet
 * @param memoryState The memory state represents the state of this file that was not yet persisted.
 */
class ProjectVirtualFile(fs: ProjectVirtualFileSystem, url: String, file: Option[File], var memoryState: Option[String] = None) extends VirtualFile {

  override def fs(): VirtualFileSystem = fs

  override def read(): String = {
    memoryState match {
      case Some(content) => content
      case _ => {
        val source = Source.fromFile(file.get, "UTF-8")
        try {
          source.mkString
        } finally {
          source.close()
        }
      }
    }
  }

  override def write(content: String): Boolean = {
    if (memoryState.isEmpty || !memoryState.get.equals(content)) {
      memoryState = Some(content)
      true
    } else {
      false
    }
  }


  override def url(): String = {
    this.url
  }

  override def asResource(): WeaveResource = super.asResource()

  def save(): ProjectVirtualFile = {
    //If file doesn't exists then we should always have the memory state
    if (file.isDefined) {
      memoryState = None
    }
    this
  }

  override def getNameIdentifier: NameIdentifier = {
    if (file.isDefined) {
      fs.sourceRoot match {
        case Some(rootFolder) => {
          val relativePath = rootFolder.toPath.relativize(file.get.toPath).toString
          NameIdentifierHelper.fromWeaveFilePath(relativePath)
        }
        case None => {
          NameIdentifierHelper.fromWeaveFilePath(file.get.getPath)
        }
      }
    } else {
      val maybeUri = URLUtils.toPath(url)
      fs.sourceRoot match {
        case Some(rootFolder) if (maybeUri.isDefined) => {
          val relativePath = rootFolder.toPath.relativize(maybeUri.get).toString
          NameIdentifierHelper.fromWeaveFilePath(relativePath)
        }
        case _ => {
          NameIdentifierHelper.fromWeaveFilePath(url)
        }
      }
    }
  }

  def closed(): ProjectVirtualFile = {
    memoryState = None
    fs.closed(url)
    this
  }

  override def readOnly(): Boolean = {
    false
  }

  override def path(): String = {
    URLUtils.toURI(url).map(_.getPath).getOrElse(url)
  }
}
