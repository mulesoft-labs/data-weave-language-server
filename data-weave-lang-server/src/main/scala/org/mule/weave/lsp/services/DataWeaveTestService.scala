package org.mule.weave.lsp.services

import net.liftweb.json.DefaultFormats
import net.liftweb.json.parseOpt
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.eclipse.lsp4j
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.Position
import org.mule.weave.dsp.OutputListener
import org.mule.weave.dsp.RunWTFConfiguration
import org.mule.weave.lsp.extension.client.PublishTestItemsParams
import org.mule.weave.lsp.extension.client.PublishTestResultsParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveTestItem
import org.mule.weave.lsp.jobs.JobManagerService
import org.mule.weave.lsp.jobs.Status
import org.mule.weave.lsp.jobs.Task
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.WTFLauncher
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.CompletableFuture
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable
import scala.util.Try

class DataWeaveTestService(
          weaveLanguageClient: WeaveLanguageClient,
          virtualFileSystem: ProjectVirtualFileSystem,
          clientLogger: ClientLogger,
          jobManagerService: JobManagerService) extends ToolingService with OutputListener {

  var projectKind: ProjectKind = _
  var testsCache: mutable.HashMap[String, WeaveTestItem] = mutable.HashMap()
  var enableTestIndex: Boolean = true

  def discoverTests(projectStructure: ProjectStructure): Unit = {
    clientLogger.logInfo("Discovering Tests.")

    WeaveDirectoryUtils.wtfUnitTestFolder(projectStructure).flatMap(file => {
      FileUtils.listFiles(file, new SuffixFileFilter(".dwl"), TrueFileFilter.INSTANCE).asScala.toList
    }).foreach(file => {
      cacheTest(URLUtils.toLSPUrl(file), async = false)
    })
  }

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {

      override def onProjectStarted(project: Project): Unit = {
        discoverTests(projectKind.structure())
      }
    })

    eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {
      override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
        if (WeaveDirectoryUtils
          .wtfUnitTestFolder(projectKind.structure())
          .exists(file => URLUtils.isChildOf(uri, file))) {
          changeType match {
            case FileChangeType.Created | FileChangeType.Changed =>
              cacheTest(uri)
            case FileChangeType.Deleted =>
              deleteTest(uri)
          }

        }
      }
    })


  }

  private def deleteTest(uri: String): Unit = {
    testsCache.remove(uri)
    publishTests()
  }

  def cacheTest(uri: String, async: Boolean = true) = {
    clientLogger.logInfo(s"Indexing tests of $uri")
    if (enableTestIndex) {
      val launcher = new WTFLauncher(projectKind, clientLogger, weaveLanguageClient, virtualFileSystem)
      val nameIdentifier = virtualFileSystem.file(uri).getNameIdentifier

      val config = RunWTFConfiguration(Some(nameIdentifier.name), None, buildBefore = true, TcpClientProtocol.DEFAULT_PORT, dryRun = true)

      val jobExecutor: (Task, String, String) => Unit = if (async) {
        jobManagerService.schedule
      } else {
        jobManagerService.execute
      }
      jobExecutor(
        (status: Status) => {
          val process = launcher.launch(config, debugging = false)

          val maybeOutput = if (process.isDefined) {
            //Block the process
            val exitValue = process.get.waitFor()
            clientLogger.logInfo(s"Process Finished with ${exitValue}")
            if (exitValue == 0) {
              val outStr = IOUtils.toString(process.get.getInputStream, StandardCharsets.UTF_8)
              Some(outStr)
            } else {
              None
            }
          } else {
            None
          }

          maybeOutput
            .map(s => parseTestEvents(s, uri, nameIdentifier.name))
            .filter(_.children.size() > 0)
            .foreach(weaveItem => {
              testsCache.put(uri, weaveItem)
              publishTests()
            })
        },
        "Indexing Tests",
        "Indexing Tests",
      )
    } else {
      clientLogger.logInfo("Skipping test indexing, update wtf dependency.")
    }
  }

  def publishTests(): Unit = {
    val weaveTestItems: util.List[WeaveTestItem] = new util.ArrayList[WeaveTestItem]()
    testsCache.values.foreach(cachedItem => weaveTestItems.add(cachedItem))
    weaveLanguageClient.publishTestItems(PublishTestItemsParams(weaveTestItems))
  }

  def publishTestResult(event: TestEvent): Unit = {
    weaveLanguageClient.publishTestResults(PublishTestResultsParams(event.event, event.message.getOrElse(""), event.name, Integer.parseInt(event.duration.getOrElse("0")), event.locationHint.getOrElse(""), event.status.getOrElse("")))
  }

  def parseTestEvents(events: String, uri: String, name: String): WeaveTestItem = {
    implicit val formats: DefaultFormats.type = DefaultFormats

    val blob = events.linesIterator.map(p => {
      parseOpt(p)
    })
    val testEvents = blob.collect({
      case Some(jValue) => jValue.extract[TestEvent]
    })

    eventsToRootTestItem(testEvents, uri, name)
  }

  def eventsToRootTestItem(events: Iterator[TestEvent], uri: String, name: String): WeaveTestItem = {
    val rootTestItem = WeaveTestItem(label = name, uri = uri)
    val parentPerId = collection.mutable.Map[String, WeaveTestItem]("-1" -> rootTestItem)

    val (finishedEvents, otherEvents) = events.partition(event => event.event == "testFinished")
    // If some test finished, WTF doesn't have dryRuns enabled
    enableTestIndex = finishedEvents
      .forall(event => event.status.contains("SKIP"))

    otherEvents
      .filter(event => event.event == "testStarted" || event.event == "testSuiteStarted")
      .foreach(event => {
        val parentId = event.parentNodeId.getOrElse("-1")
        for {
          nodeId <- event.nodeId
          parent <- parentPerId.get(parentId)
        } yield {
          val testLocation = event
            .location
            .flatMap(parseOpt)
            .flatMap(p => {
              implicit val formats: DefaultFormats.type = DefaultFormats
              p.extractOpt[TestLocation]
            })
          val range = for {
            testLoc <- testLocation
            range <- testLoc.toRange
            source <- testLoc.source
            weaveResource <- virtualFileSystem.asResourceResolver.resolve(NameIdentifier(source))
            if (weaveResource.url() == uri)
          } yield {
            range
          }
          val testItem = WeaveTestItem(label = event.name, uri = uri, range = range.orNull)
          parent.children.add(testItem)
          parentPerId += (nodeId -> testItem)
        }
      })
    rootTestItem
  }

  // TODO use this events to create WeaveTestItems as well
  def output(jsonLine: String): Unit = {
    implicit val formats: DefaultFormats.type = DefaultFormats
    try {
      parseOpt(jsonLine) match {
        case Some(jValue) => publishTestResult(jValue.extract[TestEvent])
        case None =>
      }
    } catch {
      case exception: Throwable => clientLogger.logError("[DataWeaveTestService] Error while outputting results", exception)
    }
  }

}

case class TestEvent(event: String, name: String, message: Option[String], error: Option[String], duration: Option[String], locationHint: Option[String], nodeId: Option[String], status: Option[String], parentNodeId: Option[String], captureStandardOutput: Option[String], location: Option[String])

case class TestLocation(source: Option[String], start: Option[TestPosition], end: Option[TestPosition]) {
  def toRange: Option[lsp4j.Range] = {
    for {
      start <- start
      end <- end
      startPos <- start.toPosition
      endPos <- end.toPosition
    } yield {
      new lsp4j.Range(startPos, endPos)
    }
  }
}

case class TestPosition(line: Option[String], column: Option[String]) {
  def toPosition: Option[Position] = {
    for {
      lineS <- line
      line <- Try(lineS.toInt).toOption
      columnS <- column
      column <- Try(columnS.toInt).toOption
    } yield {
      new Position(line, column)
    }
  }
}
