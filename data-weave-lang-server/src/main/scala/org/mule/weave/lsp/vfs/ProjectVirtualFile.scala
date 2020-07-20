package org.mule.weave.lsp.vfs

import java.io.File

import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.NameIdentifierHelper

import scala.io.Source

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

  override def write(content: String): Unit = {
    if (memoryState.isEmpty || !memoryState.get.equals(content)) {
      memoryState = Some(content)
      fs.onChanged(this)
    }
  }

  def save(): ProjectVirtualFile = {
    memoryState = None
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
      NameIdentifierHelper.fromWeaveFilePath(url)
    }
  }

  def closed(): ProjectVirtualFile = {
    memoryState = None
    fs.close(url)
    this
  }

  override def readOnly(): Boolean = {
    false
  }

  override def path(): String = {
    url
  }
}
