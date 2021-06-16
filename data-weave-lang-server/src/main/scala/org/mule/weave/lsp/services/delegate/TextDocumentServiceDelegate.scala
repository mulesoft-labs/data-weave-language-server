package org.mule.weave.lsp.services.delegate

import org.eclipse.lsp4j
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ResolveTypeHierarchyItemParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages
import org.mule.weave.lsp.extension.services.DidFocusChangeParams
import org.mule.weave.lsp.extension.services.WeaveTextDocumentService

import java.util
import java.util.concurrent.CompletableFuture

class TextDocumentServiceDelegate extends WeaveTextDocumentService {

  var delegate: WeaveTextDocumentService = _

  override def didOpen(params: DidOpenTextDocumentParams): Unit = {
    if (delegate != null) {
      delegate.didOpen(params)
    }
  }

  override def didChange(params: DidChangeTextDocumentParams): Unit = {
    if (delegate != null) {
      delegate.didChange(params)
    }
  }

  override def didClose(params: DidCloseTextDocumentParams): Unit = {
    if (delegate != null) {
      delegate.didClose(params)
    }
  }

  override def didSave(params: DidSaveTextDocumentParams): Unit = {
    if (delegate != null) {
      delegate.didSave(params)
    }
  }

  override def didFocusChange(params: DidFocusChangeParams): Unit = {
    if (delegate != null) {
      delegate.didFocusChange(params)
    }
  }

  override def completion(position: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = delegate.completion(position)

  override def resolveCompletionItem(unresolved: CompletionItem): CompletableFuture[CompletionItem] = delegate.resolveCompletionItem(unresolved)

  override def hover(params: HoverParams): CompletableFuture[Hover] = delegate.hover(params)

  override def signatureHelp(params: SignatureHelpParams): CompletableFuture[SignatureHelp] = delegate.signatureHelp(params)

  override def declaration(params: DeclarationParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = delegate.declaration(params)

  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = delegate.definition(params)

  override def typeDefinition(params: TypeDefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = delegate.typeDefinition(params)

  override def implementation(params: ImplementationParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = delegate.implementation(params)

  override def references(params: ReferenceParams): CompletableFuture[util.List[_ <: Location]] = delegate.references(params)

  override def documentHighlight(params: DocumentHighlightParams): CompletableFuture[util.List[_ <: DocumentHighlight]] = delegate.documentHighlight(params)

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = delegate.documentSymbol(params)

  override def codeAction(params: CodeActionParams): CompletableFuture[util.List[messages.Either[Command, CodeAction]]] = delegate.codeAction(params)

  override def codeLens(params: CodeLensParams): CompletableFuture[util.List[_ <: CodeLens]] = delegate.codeLens(params)

  override def resolveCodeLens(unresolved: CodeLens): CompletableFuture[CodeLens] = delegate.resolveCodeLens(unresolved)

  override def formatting(params: DocumentFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = delegate.formatting(params)

  override def rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = delegate.rangeFormatting(params)

  override def onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = delegate.onTypeFormatting(params)

  override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = delegate.rename(params)

  override def willSave(params: WillSaveTextDocumentParams): Unit = delegate.willSave(params)

  override def willSaveWaitUntil(params: WillSaveTextDocumentParams): CompletableFuture[util.List[TextEdit]] = delegate.willSaveWaitUntil(params)

  override def documentLink(params: DocumentLinkParams): CompletableFuture[util.List[DocumentLink]] = delegate.documentLink(params)

  override def documentLinkResolve(params: DocumentLink): CompletableFuture[DocumentLink] = delegate.documentLinkResolve(params)

  override def documentColor(params: DocumentColorParams): CompletableFuture[util.List[ColorInformation]] = delegate.documentColor(params)

  override def colorPresentation(params: ColorPresentationParams): CompletableFuture[util.List[ColorPresentation]] = delegate.colorPresentation(params)

  override def foldingRange(params: FoldingRangeRequestParams): CompletableFuture[util.List[FoldingRange]] = delegate.foldingRange(params)

  override def prepareRename(params: PrepareRenameParams): CompletableFuture[messages.Either[lsp4j.Range, PrepareRenameResult]] = delegate.prepareRename(params)

  override def typeHierarchy(params: TypeHierarchyParams): CompletableFuture[TypeHierarchyItem] = delegate.typeHierarchy(params)

  override def resolveTypeHierarchy(params: ResolveTypeHierarchyItemParams): CompletableFuture[TypeHierarchyItem] = delegate.resolveTypeHierarchy(params)

  override def prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture[util.List[CallHierarchyItem]] = delegate.prepareCallHierarchy(params)

  override def callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture[util.List[CallHierarchyIncomingCall]] = delegate.callHierarchyIncomingCalls(params)

  override def callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture[util.List[CallHierarchyOutgoingCall]] = delegate.callHierarchyOutgoingCalls(params)

  override def selectionRange(params: SelectionRangeParams): CompletableFuture[util.List[SelectionRange]] = delegate.selectionRange(params)

}
