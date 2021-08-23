package org.mule.weave.lsp.services

import net.liftweb.json.DefaultFormats
import net.liftweb.json.parseOpt
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.eclipse.lsp4j
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.Position
import org.mule.weave.dsp.OutputListener
import org.mule.weave.lsp.extension.client.PublishTestItemsParams
import org.mule.weave.lsp.extension.client.PublishTestResultsParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveTestItem
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.utils.WeaveASTQueryUtils
import org.mule.weave.lsp.utils.WeaveASTQueryUtils.WTF
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.ast.functions.FunctionCallNode
import org.mule.weave.v2.parser.ast.functions.FunctionCallParametersNode
import org.mule.weave.v2.parser.ast.structure.StringNode
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.parser.ast.variables.VariableReferenceNode

import java.util
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable

class DataWeaveTestService(weaveLanguageClient: WeaveLanguageClient, virtualFileSystem: VirtualFileSystem, dataWeaveToolingService: DataWeaveToolingService, clientLogger: ClientLogger) extends ToolingService with OutputListener {

  var projectKind: ProjectKind = _
  var testsCache: mutable.HashMap[NameIdentifier, WeaveTestItem] = mutable.HashMap()

  def discoverTests(projectStructure: ProjectStructure): Unit = {
    WeaveDirectoryUtils.wtfUnitTestFolder(projectStructure).flatMap(file => {
      FileUtils.listFiles(file, new SuffixFileFilter(".dwl"), TrueFileFilter.INSTANCE).asScala.toList
    }).foreach(file => {
      cacheTest(URLUtils.toLSPUrl(file))
    })
    publishTests()
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
        if (
          WeaveDirectoryUtils.wtfUnitTestFolder(projectKind.structure()).exists(file => URLUtils
            .isChildOf(uri, file))) {
          cacheTest(uri)
          publishTests()
        }
      }
    })


  }


  def weaveItem(stringNode: AstNode, uri: String, maybeParentWeaveItem: Option[WeaveTestItem], rootTest: Option[WeaveTestItem]): Option[WeaveTestItem] = {
    var parentWeaveItem = maybeParentWeaveItem
    stringNode match {
      //TODO this could be not a string node but a variable reference to a string value.
      case StringNode(name) => {
        val location = stringNode
          .location()
        val range = new lsp4j.Range(new Position(location.startPosition.line, location.startPosition.column), new Position(location.startPosition.line, location.startPosition.column))
        val item = WeaveTestItem(label = name, uri = uri, range = range)
        parentWeaveItem.map(parentItem => parentItem.children.add(item))
        parentWeaveItem = Some(item)
      }
    }
    parentWeaveItem
  }

  def getWeaveItem(uri: String, maybeAstNode: Option[AstNode], maybeParentWeaveItem: Option[WeaveTestItem], rootTest: Option[WeaveTestItem]): Option[WeaveTestItem] = {
    var root = rootTest
    maybeAstNode match {
      case Some(FunctionCallNode(VariableReferenceNode(NameIdentifier("describedBy", _)), FunctionCallParametersNode(Seq(stringNode, _*)))) => {
        val maybeItem = weaveItem(stringNode, uri, maybeParentWeaveItem, rootTest)
        maybeAstNode.get.children()
          .foreach(childNode => {
            val maybeChildItem = getWeaveItem(uri, Some(childNode), maybeItem, rootTest.orElse(maybeItem))
            root = root.orElse(maybeChildItem)
          })
        root = root.orElse(maybeItem)
      }
      case Some(FunctionCallNode(VariableReferenceNode(NameIdentifier("in", _)), FunctionCallParametersNode(Seq(stringNode, _*)))) => {
        val maybeItem = weaveItem(stringNode, uri, maybeParentWeaveItem, rootTest)
        maybeAstNode.get.children()
          .foreach(childNode => {
            val maybeChildItem = getWeaveItem(uri, Some(childNode), maybeItem, rootTest.orElse(maybeItem))
            root = root.orElse(maybeChildItem)
          })
        root = root.orElse(maybeItem)
      }
      case Some(node) => {
        node.children()
          .foreach(childNode => {
            val maybeChildItem = getWeaveItem(uri, Some(childNode), maybeParentWeaveItem, rootTest)
            root = root.orElse(maybeChildItem)
          })

      }
    }
    root
  }

  def cacheTest(uri: String): Unit = {
    val nameIdentifier = virtualFileSystem.file(uri).getNameIdentifier
    val maybeAstNode = dataWeaveToolingService.openDocument(uri).ast()
    val maybeString = WeaveASTQueryUtils.fileKind(maybeAstNode)
    val maybeItem = maybeString match {
      case Some(WTF) => {
        getWeaveItem(uri, maybeAstNode, None, None).map(rootItem => {
          val fileTestItemChildren: util.List[WeaveTestItem] = new util.ArrayList[WeaveTestItem]()
          fileTestItemChildren.add(rootItem)
          WeaveTestItem(label = nameIdentifier.name,uri = uri,children = fileTestItemChildren);
        })
      }
      case _ => None
    }
    maybeItem.map(weaveItem => testsCache.put(nameIdentifier, weaveItem))
  }

  def publishTests(): Unit = {
    val weaveTestItems: util.List[WeaveTestItem] = new util.ArrayList[WeaveTestItem]()
    testsCache.values.foreach(cachedItem => weaveTestItems.add(cachedItem))
    weaveLanguageClient.publishTestItems(PublishTestItemsParams(weaveTestItems))
  }


  def publishTestResult(event: TestEvent): Unit = {
    weaveLanguageClient.publishTestResults(PublishTestResultsParams(event.event, event.message.getOrElse(""), event.name, Integer.parseInt(event.duration.getOrElse("0")), event.locationHint.getOrElse(""), event.status.getOrElse("")))
  }

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

case class TestEvent(event: String, name: String, message: Option[String], error: Option[String], duration: Option[String], locationHint: Option[String], nodeId: Option[String], status: Option[String], parentNodeId: Option[String], captureStandardOutput: Option[String])
