package org.mule.weave.lsp.vfs

import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.events.DependencyArtifactRemovedEvent
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.project.events.OnDependencyArtifactRemoved
import org.mule.weave.lsp.project.events.OnDependencyArtifactResolved
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.events.LibraryAddedEvent
import org.mule.weave.lsp.vfs.events.LibraryRemovedEvent
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.util
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.JavaConverters.asJavaIteratorConverter
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.mutable

/**
  * A virtual file system that handles Maven Libraries. This VFS allows to load and unload libraries.
  *
  * @param maven                The Maven Dependency Manager
  * @param messageLoggerService The user logger
  */
class LibrariesVirtualFileSystem(eventBus: EventBus, clientLogger: ClientLogger) extends VirtualFileSystem {

  private val logger: Logger = Logger.getLogger(getClass.getName)

  private val libraries: mutable.Map[String, ArtifactVirtualFileSystem] = new mutable.HashMap()

  eventBus.register(DependencyArtifactResolvedEvent.ARTIFACT_RESOLVED, new OnDependencyArtifactResolved {
    override def onArtifactsResolved(artifacts: Array[DependencyArtifact]): Unit = {
      artifacts.foreach((artifact) => {
        val libraryVFS = if (!artifact.artifact.isDirectory) {
          new JarVirtualFileSystem(artifact.artifactId, artifact.artifact)
        } else {
          new FolderVirtualFileSystem(artifact.artifactId, artifact.artifact)
        }
        addLibrary(artifact.artifactId, libraryVFS)
      })
    }
  })

  eventBus.register(DependencyArtifactRemovedEvent.ARTIFACT_REMOVED, new OnDependencyArtifactRemoved {
    override def onArtifactsRemoved(artifacts: Array[DependencyArtifact]): Unit = {
      artifacts.foreach((a) => {
        removeLibrary(a.artifactId)
      })
    }
  })

  override def file(path: String): VirtualFile = {
    logger.log(Level.INFO, s"file ${path}")
    libraries
      .toStream
      .flatMap((vfs) => {
        logger.log(Level.INFO, "Module:" + vfs._1)
        Option(vfs._2.file(path))
      })
      .headOption
      .orNull
  }


  private def removeLibrary(name: String): Unit = {
    val maybeFileSystem: Option[ArtifactVirtualFileSystem] = libraries.remove(name)
    maybeFileSystem.foreach((vfs) => {
      clientLogger.logInfo(s"Artifact `${vfs.artifactId()}` was removed.")
      eventBus.fire(new LibraryRemovedEvent(vfs))
    })
  }

  def addLibrary(libs: Array[(String, ArtifactVirtualFileSystem)]): Unit = {
    libs.foreach((lib) => {
      val virtualFileSystem: ArtifactVirtualFileSystem = lib._2
      val name: String = lib._1
      this.libraries.put(name, virtualFileSystem)
      clientLogger.logInfo(s"Artifact `${virtualFileSystem.artifactId()}` was resolved.")
    })
    eventBus.fire(new LibraryAddedEvent(this.libraries.values.toArray))
  }

  private def addLibrary(name: String, virtualFileSystem: ArtifactVirtualFileSystem): Unit = {
    libraries.put(name, virtualFileSystem)
    clientLogger.logInfo(s"Artifact `${virtualFileSystem.artifactId()}` was resolved.")
    eventBus.fire(new LibraryAddedEvent(Array(virtualFileSystem)))
  }

  def getLibrary(name: String): VirtualFileSystem = {
    libraries.get(name).orNull
  }

  def getLibraries(): mutable.Map[String, ArtifactVirtualFileSystem] = {
    libraries
  }

  override def changeListener(cl: ChangeListener): Unit = {
    libraries.foreach(_._2.changeListener(cl))
  }

  override def onChanged(virtualFile: VirtualFile): Unit = {
    libraries.foreach(_._2.onChanged(virtualFile))
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    libraries.foreach(_._2.removeChangeListener(service))
  }

  override def asResourceResolver: WeaveResourceResolver = {
    new LibrariesWeaveResourceResolver()
  }


  override def listFiles(): util.Iterator[VirtualFile] = {
    libraries.values.toIterator.flatMap(_.listFiles().asScala).asJava
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

  private def resourceResolver: Iterator[WeaveResourceResolver] = {
    libraries.values.iterator.map(_.asResourceResolver)
  }
}


