package org.mule.weave.lsp.indexer

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.indexer.events.IndexingFinishedEvent
import org.mule.weave.lsp.indexer.events.IndexingStartedEvent
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.ProjectStructure.isAProjectFile
import org.mule.weave.lsp.project.service.ToolingService
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
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import scala.collection.JavaConverters.asJavaIteratorConverter
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.concurrent.TrieMap


class LSPWeaveIndexService(clientLogger: ClientLogger,
                           weaveLanguageClient: WeaveLanguageClient,
                           projectVirtualFileSystem: ProjectVirtualFileSystem
                          ) extends WeaveIndexService with ToolingService {

  private val identifiersInLibraries: TrieMap[VirtualFileSystem, TrieMap[String, Array[LocatedResult[WeaveIdentifier]]]] = TrieMap()
  private val identifiersInProject: TrieMap[String, Array[LocatedResult[WeaveIdentifier]]] = TrieMap()
  private val namesInLibraries: TrieMap[VirtualFileSystem, TrieMap[String, LocatedResult[WeaveDocument]]] = TrieMap()
  private val namesInProject: TrieMap[String, LocatedResult[WeaveDocument]] = TrieMap()
  private val indexedPool = new ForkJoinPool()
  private var projectKind: ProjectKind = _
  private var eventBus: EventBus = _

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.eventBus = eventBus
    this.projectKind = projectKind
    projectVirtualFileSystem.changeListener(new ChangeListener {
      override def onDeleted(vf: VirtualFile): Unit = {
        identifiersInProject.remove(vf.url())
      }

      override def onChanged(vf: VirtualFile): Unit = {
        val projectStructure = projectKind.structure()
        if (isAProjectFile(vf, projectStructure)) {
          indexProjectFile(vf)
        }
      }

      override def onCreated(vf: VirtualFile): Unit = {
        val projectStructure: ProjectStructure = projectKind.structure()
        if (isAProjectFile(vf, projectStructure)) {
          indexProjectFile(vf)
        }
      }
    })

    eventBus.register(LibraryRemovedEvent.LIBRARY_REMOVED, new OnLibraryRemoved {
      override def onLibraryRemoved(lib: VirtualFileSystem): Unit = {
        identifiersInLibraries.remove(lib)
      }
    })

    eventBus.register(LibraryAddedEvent.LIBRARY_ADDED, new OnLibraryAdded {
      override def onLibrariesAdded(libaries: Array[ArtifactVirtualFileSystem]): Unit = {
        val forks: Array[Callable[ArtifactVirtualFileSystem]] = libaries.map((vfs) => {
          new Callable[ArtifactVirtualFileSystem] {
            override def call(): ArtifactVirtualFileSystem = {
              val virtualFiles: Iterator[VirtualFile] = vfs.listFiles().asScala
              val vfsIdentifiers: TrieMap[String, Array[LocatedResult[WeaveIdentifier]]] = identifiersInLibraries.getOrElseUpdate(vfs, TrieMap())
              val vfsNames: TrieMap[String, LocatedResult[WeaveDocument]] = namesInLibraries.getOrElseUpdate(vfs, TrieMap())
              clientLogger.logInfo(s"Start Indexing `${vfs.artifactId()}`.")
              val startTime = System.currentTimeMillis()
              virtualFiles.foreach((vf) => {
                val indexer: DefaultWeaveIndexer = new DefaultWeaveIndexer()
                //Only index DW files for now
                //TODO figure out how to support other formats like .class or .raml etc
                if (vf.url().endsWith(".dwl") && indexer.parse(vf, ParsingContextFactory.createParsingContext(false))) {
                  vfsIdentifiers.put(vf.url(), index(vf, indexer))
                  vfsNames.put(vf.url(), LocatedResult(vf.getNameIdentifier, indexer.document()))
                }
              })
              clientLogger.logInfo(s"Indexing `${vfs.artifactId()}` took ${System.currentTimeMillis() - startTime}ms")
              vfs
            }
          }
        })
        val start = System.currentTimeMillis()
        eventBus.fire(new IndexingStartedEvent())
        indexedPool.invokeAll(util.Arrays.asList(forks: _*))
        eventBus.fire(new IndexingFinishedEvent())
        clientLogger.logInfo(s"Indexing all libraries finished and took ${System.currentTimeMillis() - start}ms.")
        weaveLanguageClient.showMessage(new MessageParams(MessageType.Info, "Project Indexed"))
      }
    })


  }


  override def start(): Unit = {
    eventBus.fire(new IndexingStartedEvent())
    indexedPool.invoke(ForkJoinTask.adapt(
      new Runnable {
        override def run(): Unit = {
          val value: util.Iterator[VirtualFile] = projectVirtualFileSystem.listFiles()
          while (value.hasNext) {
            indexProjectFile(value.next())
          }
        }
      })
    )
    eventBus.fire(new IndexingFinishedEvent())
  }

  private def indexProjectFile(vf: VirtualFile) = {
    val indexer: DefaultWeaveIndexer = new DefaultWeaveIndexer()
    if (indexer.parse(vf, ParsingContextFactory.createParsingContext(false))) {
      identifiersInProject.put(vf.url(), index(vf, indexer))
      namesInProject.put(vf.url(), LocatedResult(vf.getNameIdentifier, indexer.document()))
    }
  }


  private def index(vf: VirtualFile, indexer: DefaultWeaveIndexer): Array[LocatedResult[WeaveIdentifier]] = {
    val nameIdentifier = vf.getNameIdentifier
    val weaveSymbols: Array[WeaveIdentifier] = indexer.identifiers()
    val locatedWeaveSymbols = weaveSymbols.map((symbol) => {
      LocatedResult[WeaveIdentifier](nameIdentifier, symbol)
    })
    locatedWeaveSymbols
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
    (projectNames.toIterator ++ libraryNames.toIterator).asJava
  }
}
