package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URL

import org.mule.weave.lsp.services.ProjectDefinitionService
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.ChainedWeaveResourceResolver
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

class ProjectVirtualFileSystem(server: ProjectDefinitionService) extends VirtualFileSystem {

  private val inMemoryFiles: mutable.Map[String, FileBasedVirtualFile] = mutable.Map[String, FileBasedVirtualFile]()
  private val listeners: ArrayBuffer[ChangeListener] = ArrayBuffer[ChangeListener]()


  def update(uri: String, content: String): Unit = {
    println(s"[ProjectVirtualFileSystem] update ${uri} -> ${content}")

    Option(file(uri)) match {
      case Some(vf) => vf.write(content)
      case None => {
        val virtualFile = new FileBasedVirtualFile(this, uri, None, Some(content))
        inMemoryFiles.put(uri, virtualFile)
      }
    }
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
    onChanged(file(uri))
  }

  def deleted(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] deleted ${uri}")
    inMemoryFiles.remove(uri)
    onChanged(new FileBasedVirtualFile(this, uri, None))
  }

  def created(uri: String): Unit = {
    println(s"[ProjectVirtualFileSystem] created ${uri}")
    onChanged(new FileBasedVirtualFile(this, uri, None))
  }

  override def changeListener(cl: ChangeListener): Unit = {
    listeners.+=(cl)
  }

  override def onChanged(vf: VirtualFile): Unit = {
    listeners.foreach((cl) => {
      cl.onChanged(vf)
    })
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    listeners.remove(listeners.indexOf(service))
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
      case Some(rootFile) => new RootDirectoryResourceResolver(rootFile)
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
            file(f.toURI.toURL.toString)
          })
      }
      case None => {
        dependencies.listFilesByNameIdentifier(filter)
      }
    }
  }
}

class RootDirectoryResourceResolver(root: File) extends WeaveResourceResolver {

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path = NameIdentifierHelper.toWeaveFilePath(name)
    val theFile = new File(root, path)
    if (theFile.exists()) {
      val source = Source.fromFile(theFile, "UTF-8")
      try {
        Some(WeaveResource(theFile.toURI.toURL.toString, source.mkString))
      } finally {
        source.close()
      }
    } else {
      None
    }
  }
}
