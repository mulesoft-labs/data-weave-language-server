package org.mule.weave.lsp.indexer

import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent
import org.mule.weave.lsp.indexer.events.IndexingStartedEvent
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.vfs.ArtifactVirtualFileSystem
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.lsp.vfs.events.LibraryAddedEvent
import org.mule.weave.lsp.vfs.events.LibraryRemovedEvent
import org.mule.weave.lsp.vfs.events.OnLibraryAdded
import org.mule.weave.lsp.vfs.events.OnLibraryRemoved
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.editor.indexing.DefaultWeaveIndexer
import org.mule.weave.v2.editor.indexing.IdentifierKind
import org.mule.weave.v2.editor.indexing.LocatedResult
import org.mule.weave.v2.editor.indexing.WeaveDocument
import org.mule.weave.v2.editor.indexing.WeaveIdentifier
import org.mule.weave.v2.editor.indexing.WeaveIndexService
import org.mule.weave.v2.sdk.ParsingContextFactory

import java.util
import java.util.concurrent.Executor
import scala.collection.JavaConverters.asJavaIteratorConverter
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.concurrent.TrieMap

class LSPWeaveIndexService(eventBus: EventBus, executor: Executor, clientLogger: ClientLogger, projectVirtualFileSystem: ProjectVirtualFileSystem) extends WeaveIndexService {

  private val identifiersInLibraries: TrieMap[VirtualFileSystem, TrieMap[String, Array[LocatedResult[WeaveIdentifier]]]] = TrieMap()
  private val identifiersInProject: TrieMap[String, Array[LocatedResult[WeaveIdentifier]]] = TrieMap()
  private val namesInLibraries: TrieMap[VirtualFileSystem, TrieMap[String, LocatedResult[WeaveDocument]]] = TrieMap()
  private val namesInProject: TrieMap[String, LocatedResult[WeaveDocument]] = TrieMap()

  projectVirtualFileSystem.changeListener(new ChangeListener {
    override def onDeleted(vf: VirtualFile): Unit = {
      identifiersInProject.remove(vf.url())
    }

    override def onChanged(vf: VirtualFile): Unit = {
      indexProjectFile(vf)

    }

    override def onCreated(vf: VirtualFile): Unit = {
      indexProjectFile(vf)
    }
  })

  private def indexProjectFile(vf: VirtualFile) = {
    val indexer: DefaultWeaveIndexer = new DefaultWeaveIndexer()
    if (indexer.parse(vf, ParsingContextFactory.createParsingContext(false))) {
      identifiersInProject.put(vf.url(), index(vf, indexer))
      namesInProject.put(vf.url(), LocatedResult(vf.getNameIdentifier, indexer.document()))
    }
  }

  eventBus.register(LibraryAddedEvent.LIBRARY_ADDED, new OnLibraryAdded {
    override def onLibrariesAdded(vfs: ArtifactVirtualFileSystem): Unit = {
      val virtualFiles = vfs.listFiles().asScala
      val vfsIdentifiers: TrieMap[String, Array[LocatedResult[WeaveIdentifier]]] = identifiersInLibraries.getOrElseUpdate(vfs, TrieMap())
      val vfsNames: TrieMap[String, LocatedResult[WeaveDocument]] = namesInLibraries.getOrElseUpdate(vfs, TrieMap())
      executor.execute(new Runnable {
        override def run(): Unit = {
          clientLogger.logInfo(s"Start Indexing `${vfs.artifactId()}`.")
          val startTime = System.currentTimeMillis()
          virtualFiles.foreach((vf) => {
            val indexer: DefaultWeaveIndexer = new DefaultWeaveIndexer()
            //Only index DW files for now
            //TODO figure out how to support other formats like .class or .raml etc
            if (vf.url().endsWith(".dwl") && indexer.parse(vf, ParsingContextFactory.createParsingContext(false))) {
              eventBus.fire(new IndexingStartedEvent(vfs))
              vfsIdentifiers.put(vf.url(), index(vf, indexer))
              vfsNames.put(vf.url(), LocatedResult(vf.getNameIdentifier, indexer.document()))
              eventBus.fire(new IndexingFinishedEvent(vfs))
            }
          })
          clientLogger.logInfo(s"Indexing `${vfs.artifactId()}` took ${System.currentTimeMillis() - startTime}ms")
        }
      })
    }
  })

  private def index(vf: VirtualFile, indexer: DefaultWeaveIndexer): Array[LocatedResult[WeaveIdentifier]] = {
    val nameIdentifier = vf.getNameIdentifier
    val weaveSymbols: Array[WeaveIdentifier] = indexer.identifiers()
    val locatedWeaveSymbols = weaveSymbols.map((symbol) => {
      LocatedResult[WeaveIdentifier](nameIdentifier, symbol)
    })
    locatedWeaveSymbols
  }

  eventBus.register(LibraryRemovedEvent.LIBRARY_REMOVED, new OnLibraryRemoved {
    override def onLibraryRemoved(lib: VirtualFileSystem): Unit = {
      identifiersInLibraries.remove(lib)
    }
  })

  def init(): Unit = {
    executor.execute(new Runnable {
      override def run(): Unit = {
        eventBus.fire(new IndexingStartedEvent(projectVirtualFileSystem))
        val value = projectVirtualFileSystem.listFiles()
        while (value.hasNext) {
          indexProjectFile(value.next())
        }
        eventBus.fire(new IndexingFinishedEvent(projectVirtualFileSystem))
      }
    })

  }

  override def searchReferences(name: String): Array[LocatedResult[WeaveIdentifier]] = {
    val librarySymbols = identifiersInLibraries.values.flatMap((vf) => {
      val allSymbols = vf.values
      val result = searchIn(allSymbols, name, IdentifierKind.REFERENCE)
      result
    })
    val projectSymbols = searchIn(identifiersInProject.values, name, IdentifierKind.REFERENCE)
    (projectSymbols ++ librarySymbols).toArray
  }

  private def searchIn(identifiers: Iterable[Array[LocatedResult[WeaveIdentifier]]], name: String, kind: Int): Iterable[LocatedResult[WeaveIdentifier]] = {
    identifiers
      .flatMap((identifiers) => {
        identifiers.filter((identifier) => {
          identifier.value.kind == kind &&
            identifier.value.value.equals(name)
        })
      })
  }

  override def searchDefinitions(name: String): Array[LocatedResult[WeaveIdentifier]] = {
    val librarySymbols = identifiersInLibraries.values.flatMap((vf) => {
      val allSymbols = vf.values
      val result = searchIn(allSymbols, name, IdentifierKind.DEFINITION)
      result
    })
    val projectSymbols = searchIn(identifiersInProject.values, name, IdentifierKind.DEFINITION)
    (projectSymbols ++ librarySymbols).toArray
  }

  override def searchDocumentContainingName(pattern: String): util.Iterator[LocatedResult[WeaveDocument]] = {
    val libraryNames = namesInLibraries.values.flatMap((names) => {
      names.values.filter((name) => {
        name.moduleName.name.contains(pattern)
      })
    })
    val projectNames = namesInProject.values.filter((name) => {
      name.moduleName.name.contains(pattern)
    })
    (projectNames.toIterator ++   libraryNames.toIterator).asJava
  }
}
