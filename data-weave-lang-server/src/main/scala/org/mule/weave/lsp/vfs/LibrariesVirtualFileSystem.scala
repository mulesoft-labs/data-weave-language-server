package org.mule.weave.lsp.vfs

import org.mule.weave.v2.deps.MavenDependencyAnnotationProcessor
import org.mule.weave.v2.deps.ResourceDependencyAnnotationProcessor
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class LibrariesVirtualFileSystem(maven: MavenDependencyAnnotationProcessor, resources: ResourceDependencyAnnotationProcessor) extends VirtualFileSystem {

  private val modules: mutable.Map[String, VirtualFileSystem] = new mutable.HashMap()

  private val listeners: ArrayBuffer[LibrariesChangeListener] = ArrayBuffer()

  override def file(path: String): VirtualFile = {
    println(s"[DependenciesVirtualFileSystem] file ${path}")
    modules
      .toStream
      .flatMap((vfs) => {
        println("[DependenciesVirtualFileSystem] Module:" + vfs._1)
        Option(vfs._2.file(path))
      })
      .headOption
      .orNull
  }

  def registerListener(listener: LibrariesChangeListener): Unit = {
    this.listeners += (listener)
  }

  def retrieveMavenArtifact(artifactId: String, errorMessage: (String) => Unit): Unit = {
    if (!modules.contains(artifactId)) {
      maven.retrieve(artifactId, errorMessage)
    }
  }

  def removeLibrary(name: String): Boolean = {
    val defined = modules.remove(name).isDefined
    if (defined) {
      listeners.foreach(_.onLibraryRemoved(name))
    }
    defined
  }

  def addLibrary(name: String, virtualFileSystem: VirtualFileSystem): Unit = {
    modules.update(name, virtualFileSystem)
    listeners.foreach(_.onLibraryAdded(name))
  }

  def getModule(name: String): VirtualFileSystem = {
    modules.get(name).orNull
  }

  def getModules(): mutable.Map[String, VirtualFileSystem] = {
    modules
  }

  override def changeListener(cl: ChangeListener): Unit = {
    modules.foreach(_._2.changeListener(cl))
  }

  override def onChanged(virtualFile: VirtualFile): Unit = {
    modules.foreach(_._2.onChanged(virtualFile))
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    modules.foreach(_._2.removeChangeListener(service))
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new LibrariesWeaveResourceResolver()
  }

  override def listFilesByNameIdentifier(filter: String): Array[VirtualFile] = {
    modules.values.flatMap(_.listFilesByNameIdentifier(filter)).toArray
  }

  class LibrariesWeaveResourceResolver() extends WeaveResourceResolver {

    override def resolve(name: NameIdentifier): Option[WeaveResource] = {
      val resolvers: Iterator[WeaveResourceResolver] = resourceResolver
      while (resolvers.hasNext) {
        val resolver = resolvers.next()
        val resolved: Option[WeaveResource] = resolver.resolve(name)
        if (resolved.isDefined) {
          return resolved
        }
      }
      None
    }

    override def resolvePath(path: String): Option[WeaveResource] = {
      val resolvers: Iterator[WeaveResourceResolver] = resourceResolver
      while (resolvers.hasNext) {
        val resolver = resolvers.next()
        val resolved: Option[WeaveResource] = resolver.resolvePath(path)
        if (resolved.isDefined) {
          return resolved
        }
      }
      None
    }

    override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = {
      resourceResolver.flatMap(_.resolveAll(name)).toSeq
    }
  }

  private def resourceResolver = {
    modules.values.iterator.map(_.asResourceResolver)
  }
}


trait LibrariesChangeListener {

  def onLibraryAdded(id: String): Unit = {}

  def onLibraryRemoved(id: String): Unit = {}
}