package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URL

import org.mule.weave.lsp.services.ProjectDefinition
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class ProjectVirtualFileSystem(projectDefinition: ProjectDefinition) extends VirtualFileSystem {

  private val inMemoryFiles: mutable.Map[String, ProjectVirtualFile] = mutable.Map[String, ProjectVirtualFile]()
  private val changeListeners: ArrayBuffer[ChangeListener] = ArrayBuffer[ChangeListener]()
  private val vfsChangeListeners: ArrayBuffer[VFSChangeListener] = ArrayBuffer[VFSChangeListener]()


  def update(uri: String, content: String): Unit = {
    println(s"[ProjectVirtualFileSystem] update ${uri} -> ${content}")

    Option(file(uri)) match {
      case Some(vf) => {
        vf.write(content)
        vfsChangeListeners.foreach(_.onChanged(vf))
      }
      case None => {
        val virtualFile = new ProjectVirtualFile(this, uri, None, Some(content))
        vfsChangeListeners.foreach(_.onChanged(virtualFile))
        inMemoryFiles.put(uri, virtualFile)
      }
    }
  }

  def addVFSChangeListener(listener: VFSChangeListener): Unit = {
    vfsChangeListeners.+=(listener)
  }

  def close(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] close ${uri}")
    inMemoryFiles.remove(uri)
  }

  def save(uri: String): Unit = {
    inMemoryFiles.get(uri).map(_.save())
  }

  def changed(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] changed ${uri}")
    val virtualFile = file(uri)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach(_.onChanged(virtualFile))
  }

  def deleted(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] deleted ${uri}")
    inMemoryFiles.remove(uri)
    val virtualFile = new ProjectVirtualFile(this, uri, None)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach(_.onDeleted(virtualFile))
  }

  def created(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] created ${uri}")
    val virtualFile = new ProjectVirtualFile(this, uri, None)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach(_.onCreated(virtualFile))
  }

  override def changeListener(cl: ChangeListener): Unit = {
    changeListeners.+=(cl)
  }

  override def onChanged(vf: VirtualFile): Unit = {
    triggerChanges(vf)
  }

  private def triggerChanges(vf: VirtualFile) = {
    changeListeners.foreach((cl) => {
      cl.onChanged(vf)
    })
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    changeListeners.remove(changeListeners.indexOf(service))
  }


  def sourceRoot: Option[File] = {
    //TODO: implement support for multi source folder
    projectDefinition.sourceFolder().headOption
  }

  override def file(path: String): VirtualFile = {
    println(s"[ProjectVirtualFileSystem] file ${path}")
    //absolute path
    if (inMemoryFiles.contains(path)) {
      inMemoryFiles(path)
    } else {
      //It may not be a valid url then just try on nextone
      val maybeFilePath = Try(new URL(path).getFile).toOption
      if (maybeFilePath.isEmpty) {
        null
      } else {
        val theFile = new File(maybeFilePath.get)
        if (theFile.exists()) {
          val virtualFile = new ProjectVirtualFile(this, path, Some(theFile))
          inMemoryFiles.put(path, virtualFile)
          virtualFile
        } else {
          null
        }
      }
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    val resourceResolver = sourceRoot match {
      case Some(rootFile) => new FolderWeaveResourceResolver(rootFile, this)
      case None => super.asResourceResolver
    }
    resourceResolver
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    sourceRoot match {
      case Some(rootFolder) => {
        val parts = filter.split("::")
        val headAndLast = parts.splitAt(parts.length - 1)
        val container = new File(rootFolder, headAndLast._1.mkString(File.separator))
        val files: Array[File] = container.listFiles((f) => {
          f.getName.contains(headAndLast._2.head)
        })
        if (files != null) {
          files
            .map((f) => {
              file(FileUtils.toUrl(f))
            })
        } else {
          Array()
        }

      }
      case None => {
        Array()
      }
    }
  }
}

object FileUtils {
  /**
   * Build the url according to vscode standard
   *
   * @param theFile The file to get the url from
   * @return
   */
  def toUrl(theFile: File): String = {
    "file://" + theFile.toURI.toURL.getPath
  }
}


