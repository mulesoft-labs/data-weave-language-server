package org.mule.weave.lsp

import java.util
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService

class DataWeaveDocumentService(params: InitializeParams) extends TextDocumentService {

  private var dwTextDocumentService: WeaveToolingService = _

  override def didOpen(openParam: DidOpenTextDocumentParams): Unit = {
    val uri = params.getRootUri

//    dwTextDocumentService = new WeaveToolingService()
//    val document = openParam.getTextDocument;
//    val toolingService: WeaveDocumentToolingService = dwTextDocumentService.open(openParam)
  }


  override def completion(position: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = {
    val document = position.getTextDocument
//    dwTextDocumentService.open(document.getUri).completion()
    CompletableFuture.completedFuture(null)
  }

  override def didChange(params: DidChangeTextDocumentParams): Unit = {

  }

  override def didClose(params: DidCloseTextDocumentParams): Unit = {


  }

  override def didSave(params: DidSaveTextDocumentParams): Unit = {


  }
}
