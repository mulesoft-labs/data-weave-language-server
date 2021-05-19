package org.mule.weave.lsp.vfs

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.ProjectStructure.isAProjectFile
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.URLUtils.toURI
import org.mule.weave.lsp.utils.VFUtils
import org.mule.weave.lsp.vfs.resource.FolderWeaveResourceResolver
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.ChainedWeaveResourceResolver
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import java.io.File
import java.nio.file.Path
import java.util
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.JavaConverters.asJavaIteratorConverter
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * This Virtual File System handles the project interactions.
  *
  * There are two kind of files:
  *
  *  - InMemory ones, this is when a File was either modified or created by still not being saved in the persisted FS.
  *  - Persisted Files, this are the files that are haven't been modified and are the one persisted in the FS
  *
  * @param projectDefinition
  */
class ProjectVirtualFileSystem(eventBus: EventBus, projectStructure: ProjectStructure) extends VirtualFileSystem {

  private val inMemoryFiles: mutable.Map[String, ProjectVirtualFile] = mutable.Map[String, ProjectVirtualFile]()

  private val vfsChangeListeners: ArrayBuffer[ChangeListener] = ArrayBuffer[ChangeListener]()

  private val logger: Logger = Logger.getLogger(getClass.getName)

  eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {
    override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
      changeType match {
        case FileChangeType.Created => {
          if (isAProjectFile(uri, projectStructure)) {
            created(uri)
          }
        }
        case FileChangeType.Changed => {
          if (isAProjectFile(uri, projectStructure)) {
            changed(uri)
          }
        }
        case FileChangeType.Deleted => {
          if (isAProjectFile(uri, projectStructure)) {
            deleted(uri)
          }
        }
      }
    }
  })

  def update(uri: String, content: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"Update: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"Update `${uri}` -> ${content}")
      Option(file(uri)) match {
        case Some(vf) => {
          val written: Boolean = vf.write(content)
          if (written) {
            triggerChanges(vf)
          }
        }
        case None => {
          val virtualFile: ProjectVirtualFile = new ProjectVirtualFile(this, uri, None, Some(content))
          inMemoryFiles.put(uri, virtualFile)
          triggerChanges(virtualFile)
        }
      }
    }
  }


  private def isSupportedScheme(uri: String) = {
    toURI(uri)
      .exists((uri) => {
        val scheme = uri.getScheme
        scheme == "file" || scheme == "untitled"
      })
  }

  /**
    * Mark the specified Uri as closed. All memory representation should be cleaned
    *
    * @param uri The Uri of the file
    */
  def closed(uri: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"closed: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"closed ${uri}")
      inMemoryFiles.remove(uri)
    }
  }

  /**
    * Mark the given uri as saved into the persisted FS
    *
    * @param uri The uri to be marked as saved
    */
  def saved(uri: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"saved: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"saved: ${uri}")
      inMemoryFiles.get(uri).map(_.save())
    }
  }

  /**
    * Mark a given uri was changed by an external even
    *
    * @param uri The uri to be marked as changed
    */
  private def changed(uri: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"changed: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"changed: ${uri}")
      val virtualFile = file(uri)
      triggerChanges(virtualFile)
      //TODO should we remove the inMemoryRepresentation!
      vfsChangeListeners.foreach(_.onChanged(virtualFile))
    }
  }

  /**
    * Mark a given uri was deleted by an external event
    *
    * @param uri The uri to be deleted
    */
  private def deleted(uri: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"deleted: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"deleted ${uri}")
      inMemoryFiles.remove(uri)
      val virtualFile = new ProjectVirtualFile(this, uri, URLUtils.toFile(uri))
      triggerChanges(virtualFile)
      vfsChangeListeners.foreach((listener) => {
        listener.onDeleted(virtualFile)
      })
    }
  }

  private def created(uri: String): Unit = {
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"created: Ignoring ${uri} as it is not supported. Most probably is a read only")
    } else {
      logger.log(Level.INFO, s"created: ${uri}")
      val virtualFile = new ProjectVirtualFile(this, uri, URLUtils.toFile(uri))
      triggerChanges(virtualFile)
      vfsChangeListeners.foreach((listener) => {
        listener.onCreated(virtualFile)
      })
    }
  }

  override def changeListener(cl: ChangeListener): Unit = {
    vfsChangeListeners.+=(cl)
  }

  override def onChanged(vf: VirtualFile): Unit = {
    triggerChanges(vf)
  }

  private def triggerChanges(vf: VirtualFile): Unit = {
    vfsChangeListeners.foreach((listener) => {
      listener.onChanged(vf)
    })
  }

  override def removeChangeListener(service: ChangeListener): Unit = {
    vfsChangeListeners.remove(vfsChangeListeners.indexOf(service))
  }


  override def file(uri: String): VirtualFile = {
    logger.log(Level.INFO, s"file ${uri}")
    val supportedScheme = isSupportedScheme(uri)
    if (!supportedScheme) {
      logger.log(Level.INFO, s"Ignoring ${uri} as it is not supported. Most probably is a read only")
      null
    } else {
      //absolute path
      if (inMemoryFiles.contains(uri)) {
        inMemoryFiles(uri)
      } else {
        //It may not be a valid url then just try on nextone
        val maybeFile = URLUtils.toFile(uri)
        if (maybeFile.isEmpty) {
          null
        } else {
          if (maybeFile.get.exists()) {
            val virtualFile = new ProjectVirtualFile(this, uri, maybeFile)
            inMemoryFiles.put(uri, virtualFile)
            virtualFile
          } else {
            null
          }
        }
      }
    }
  }

  def routeOf(uri: String): Option[File] = {
    val rootFolders = projectStructure.modules
      .flatMap((module) => {
        module.roots.flatMap(_.sources) ++ module.roots.flatMap(_.resources)
      })
    rootFolders
      .find((root) => {
        val maybePath: Option[Path] = URLUtils.toPath(uri)
        maybePath
          .exists((path) => {
            path.startsWith(root.toPath)
          })
      })
  }

  override def asResourceResolver: WeaveResourceResolver = {
    val resolvers = projectStructure.modules.flatMap((module) => {
      module.roots.flatMap((root) => {
        root.sources.map((root) => {
          new FolderWeaveResourceResolver(root, this)
        }) ++
          root.resources.map((root) => {
            new FolderWeaveResourceResolver(root, this)
          })
      })
    })
    val inMemoryVirtualFileResourceResolver = new InMemoryVirtualFileResourceResolver(inMemoryFiles)
    new ChainedWeaveResourceResolver(inMemoryVirtualFileResourceResolver +: resolvers)
  }


  override def listFiles(): util.Iterator[VirtualFile] = {
    projectStructure.modules.toIterator.flatMap((module) => {
      module.roots.toIterator.flatMap((root) => {
        root.sources.toIterator.flatMap((root) => {
          VFUtils.listFiles(root, this)
        }) ++
          root.resources.toIterator.flatMap((root) => {
            VFUtils.listFiles(root, this)
          })
      })
    }).asJava
  }
}

class InMemoryVirtualFileResourceResolver(inMemoryFiles: mutable.Map[String, ProjectVirtualFile]) extends WeaveResourceResolver {

  override def resolvePath(path: String): Option[WeaveResource] = {
    this.inMemoryFiles.get(path).map((f) => WeaveResource(f))
  }

  override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = {
    super.resolveAll(name)
  }

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path: String = NameIdentifierHelper.toWeaveFilePath(name, "/")
    resolvePath(path)
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


