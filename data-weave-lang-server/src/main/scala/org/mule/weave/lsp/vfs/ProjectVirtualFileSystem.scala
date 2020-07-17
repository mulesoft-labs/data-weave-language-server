package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URL

import org.mule.weave.lsp.services.ProjectDefinitionService
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.ChainedWeaveResourceResolver
import org.mule.weave.v2.sdk.DefaultWeaveResource
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

class ProjectVirtualFileSystem(server: ProjectDefinitionService) extends VirtualFileSystem {

  private val inMemoryFiles: mutable.Map[String, FileBasedVirtualFile] = mutable.Map[String, FileBasedVirtualFile]()
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
        val virtualFile = new FileBasedVirtualFile(this, uri, None, Some(content))
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
    val virtualFile = new FileBasedVirtualFile(this, uri, None)
    triggerChanges(virtualFile)
    vfsChangeListeners.foreach(_.onDeleted(virtualFile))
  }

  def created(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] created ${uri}")
    val virtualFile = new FileBasedVirtualFile(this, uri, None)
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
    server.sourceFolder().headOption
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
        dependencies.file(path)
      } else {
        val theFile = new File(maybeFilePath.get)
        if (theFile.exists()) {
          val virtualFile = new FileBasedVirtualFile(this, path, Some(theFile))
          inMemoryFiles.put(path, virtualFile)
          virtualFile
        } else {
          dependencies.file(path)
        }
      }
    }
  }

  private def dependencies: VirtualFileSystem = {
    server.dependenciesVFS()
  }

  override def asResourceResolver: WeaveResourceResolver = {
    val resourceResolver = sourceRoot match {
      case Some(rootFile) => new RootDirectoryResourceResolver(rootFile, this)
      case None => super.asResourceResolver
    }
    new ChainedWeaveResourceResolver(Seq(resourceResolver, dependencies.asResourceResolver))
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    sourceRoot match {
      case Some(rootFolder) => {
        val parts = filter.split("::")
        val headAndLast = parts.splitAt(parts.length - 1)
        val container = new File(rootFolder, headAndLast._1.mkString(File.separator))
        container.listFiles((f) => {
          f.getName.contains(headAndLast._2.head)
        })
          .map((f) => {
            file(FileUtils.toUrl(f))
          })
      }
      case None => {
        dependencies.listFilesByNameIdentifier(filter)
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

class RootDirectoryResourceResolver(root: File, vfs: VirtualFileSystem) extends WeaveResourceResolver {


  override def resolvePath(path: String): Option[WeaveResource] = {
    val theFile = new File(root, path)
    fileToResources(theFile)
  }

  override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = super.resolveAll(name)

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path = NameIdentifierHelper.toWeaveFilePath(name)
    val theFile = new File(root, path)
    fileToResources(theFile)
  }

  private def fileToResources(theFile: File): Option[DefaultWeaveResource] = {
    val vsCodeUrlStyle = FileUtils.toUrl(theFile)
    Option(vfs.file(vsCodeUrlStyle)).map((vf) => {
      WeaveResource(vf.path(), vf.read())
    })
  }
}
