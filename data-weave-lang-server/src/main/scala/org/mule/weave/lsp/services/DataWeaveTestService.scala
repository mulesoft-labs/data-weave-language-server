package org.mule.weave.lsp.services

import net.liftweb.json.DefaultFormats
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.NoTypeHints
import net.liftweb.json.Serialization
import org.eclipse.lsp4j
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.Position
import org.mule.weave.lsp.extension.client.PublishTestItemsParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveTestItem
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
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
import net.liftweb.json.parseOpt
import net.liftweb.json.Serialization.{read, write}
import net.liftweb.json.parse
import org.mule.weave.lsp.extension.client.PublishTestResultsParams

import java.util

class DataWeaveTestService(weaveLanguageClient: WeaveLanguageClient, virtualFileSystem: VirtualFileSystem, dataWeaveToolingService: DataWeaveToolingService) extends ToolingService {

  var projectKind: ProjectKind = _

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind

    eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {


      override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
        if (
          ProjectStructure.testsSourceFolders(projectKind.structure()).find((f) => f.getName == WeaveDirectoryUtils.DWTest_FOLDER).exists(file => URLUtils
            .isChildOf(uri, file))) {
          publishTests(uri);
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

  def publishTests(uri: String): Unit = {
    val maybeAstNode = dataWeaveToolingService.openDocument(uri).ast()
    val maybeString = WeaveASTQueryUtils.fileKind(maybeAstNode)
    val maybeItem = maybeString match {
      case Some(WTF) => getWeaveItem(uri, maybeAstNode, None, None)
      case _ => None
    }
    val list: util.List[WeaveTestItem] = new util.ArrayList[WeaveTestItem]()
    maybeItem.map(weaveItem => list.add(weaveItem))
    weaveLanguageClient.publishTestItems(PublishTestItemsParams(list))
  }


  def publishTestResult(event: TestEvent): Unit = {
    weaveLanguageClient.publishTestResults(PublishTestResultsParams(event.event,event.message.getOrElse(""),event.name,Integer.parseInt(event.duration.getOrElse("0")),event.locationHint.getOrElse(""),event.status.getOrElse("")))
  }

  def feedTestResult(jsonLine: String): Unit = {
    implicit val formats = DefaultFormats
    parseOpt(jsonLine) match {
      case Some(jValue) => publishTestResult(jValue.extract[TestEvent])
      case None =>
    }
  }

}

case class TestEvent(event: String, name: String, message: Option[String], error: Option[String], duration: Option[String], locationHint: Option[String], nodeId: String, status: Option[String], parentNodeId: Option[String], captureStandardOutput: Option[String])
