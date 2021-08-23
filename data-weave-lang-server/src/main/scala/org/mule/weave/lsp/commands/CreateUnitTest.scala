package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.mule.weave.lsp.extension.client.OpenTextDocumentParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.location.WeaveLocation

import java.io.File
import java.util

class CreateUnitTest(validationService: DataWeaveToolingService, weaveLanguageClient: WeaveLanguageClient, projectKind: ProjectKind) extends WeaveCommand {
  override def commandId(): String = Commands.DW_CREATE_UNIT_TEST

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val args: util.List[AnyRef] = params.getArguments

    val uri: String = Commands.argAsString(args, 0)
    val startOffset: Int = Commands.argAsInt(args, 1)
    val endOffset: Int = Commands.argAsInt(args, 2)

    val documentToolingService: WeaveDocumentToolingService = validationService.openDocument(uri)
    val maybeTestPath: Option[String] = documentToolingService.getTestPathFromDefinition(startOffset, endOffset)
    val files = ProjectStructure.testsSourceFolders(projectKind.structure())
    val maybeFile: Option[File] = files.find((f) => f.getName == WeaveDirectoryUtils.DWTest_FOLDER)

    for {
      testPath <- maybeTestPath
      weaveTestFolder <- maybeFile
    } yield {
      val testFile = new File(weaveTestFolder, testPath)
      val testFileURL = toLSPUrl(testFile)
      if (testFile.exists()){
        val testToolingService: WeaveDocumentToolingService = validationService.openDocument(testFileURL)
        for {
          ast <- testToolingService.ast()
          testAddition <- documentToolingService.addUnitTestFromDefinition(startOffset, endOffset, ast)
        } yield {
          val line = testAddition.replacementLine - 1
          val textEdit = new TextEdit(new org.eclipse.lsp4j.Range(new Position(line, 0), new Position(line, 0)), testAddition.test)
          val textDocumentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(testFileURL, 0), util.Arrays.asList(textEdit))
          val insertText = Either.forLeft[TextDocumentEdit, ResourceOperation](textDocumentEdit)

          val edits = util.Arrays.asList(insertText)

          weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
          weaveLanguageClient.openTextDocument(OpenTextDocumentParams(
            testFileURL,
            startLine = line,
            startCharacter = 0,
            endLine = line + testAddition.test.count(_ == '\n'),
            endCharacter = 0)
          )
        }
      } else {
        val maybeTest: Option[String] = documentToolingService.createUnitTestFromDefinition(startOffset, endOffset)
        maybeTest.map((test) => {
          val createFile = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(testFileURL))
          val textEdit = new TextEdit(new org.eclipse.lsp4j.Range(new Position(0, 0), new Position(0, 0)), test)
          val textDocumentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(testFileURL, 0), util.Arrays.asList(textEdit))
          val insertText = Either.forLeft[TextDocumentEdit, ResourceOperation](textDocumentEdit)
          val edits = util.Arrays.asList(createFile, insertText)

          weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
          weaveLanguageClient.openTextDocument(OpenTextDocumentParams(testFileURL))
        })
      }
    }
    null
  }
}

object CreateUnitTest {
  def createCommand(uri: String, astNode: AstNode): Command = {
    val nodeLocation: WeaveLocation = astNode.location()
    new Command("Add Unit Test",
      Commands.DW_CREATE_UNIT_TEST,
      util.Arrays.asList(
        uri,
        nodeLocation.startPosition.index: java.lang.Integer,
        nodeLocation.endPosition.index: java.lang.Integer,
      )
    )
  }

}
