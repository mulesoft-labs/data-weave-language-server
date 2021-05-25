package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.mule.weave.lsp.client.OpenTextDocumentParams
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveInputBoxResult
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.utils.URLUtils.toLSPUrl
import org.mule.weave.lsp.utils.VFUtils
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util
import scala.io.Source

class CreateTest(projectKind: ProjectKind, weaveLanguageClient: WeaveLanguageClient) extends WeaveCommand {

  val TEMPLATE_TEST: String = {
    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("dw-template-test.dwl"), "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  override def commandId(): String = Commands.DW_CREATE_TEST

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val value: WeaveInputBoxResult = weaveLanguageClient.weaveInputBox(WeaveInputBoxParams(title = "Insert The Name Of The Test")).get()
    if (!value.cancelled) {
      val nameOfTheTest = value.value
      var path = nameOfTheTest.replace(NameIdentifier.SEPARATOR, File.separator)
      if (!path.endsWith(VFUtils.DWL_EXTENSION)) {
        path = path + VFUtils.DWL_EXTENSION
      }
      val maybeFile: Option[File] = ProjectStructure.testsSourceFolders(projectKind.structure()).find((f) => f.getName == WeaveDirectoryUtils.DWTest_FOLDER)
      maybeFile match {
        case Some(weaveTestFolder) => {
          val testFile = new File(weaveTestFolder, path)
          val testFileURL = toLSPUrl(testFile)
          val createFile = Either.forRight[TextDocumentEdit, ResourceOperation](new CreateFile(testFileURL))
          val textEdit = new TextEdit(new org.eclipse.lsp4j.Range(new Position(0, 0), new Position(0, 0)), TEMPLATE_TEST)
          val textDocumentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(testFileURL, 0), util.Arrays.asList(textEdit))
          val insertText = Either.forLeft[TextDocumentEdit, ResourceOperation](textDocumentEdit)
          val edits = util.Arrays.asList(createFile, insertText)
          val response = weaveLanguageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(edits))).get()
          if (response.isApplied) {
            weaveLanguageClient.openTextDocument(OpenTextDocumentParams(testFileURL))
          }
        }
        case None =>
      }
    }else{

    }
    null
  }
}
