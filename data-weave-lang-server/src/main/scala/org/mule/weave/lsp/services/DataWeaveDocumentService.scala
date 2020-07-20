package org.mule.weave.lsp.services

import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.mule.weave.lsp.utils.LSPConverters._
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.completion.Suggestion
import org.mule.weave.v2.completion.SuggestionType
import org.mule.weave.v2.editor.ImplicitInput
import org.mule.weave.v2.editor.WeaveDocumentToolingService
import org.mule.weave.v2.editor.WeaveToolingService
import org.mule.weave.v2.editor.{SymbolKind => WeaveSymbolKind}
import org.mule.weave.v2.scope.Reference
import org.mule.weave.v2.utils.WeaveTypeEmitterConfig

import scala.collection.JavaConverters

class DataWeaveDocumentService(weaveService: LSPWeaveToolingService, executor: Executor, projectFS: ProjectVirtualFileSystem) extends TextDocumentService {

  override def didOpen(openParam: DidOpenTextDocumentParams): Unit = {
    println("[DataWeave] Open: " + openParam.getTextDocument.getUri)
    val textDocument = openParam.getTextDocument
    projectFS.update(textDocument.getUri, openParam.getTextDocument.getText)
  }

  def dwTextDocumentService: WeaveToolingService = {
    weaveService.documentService()
  }

  override def completion(position: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = {
    CompletableFuture.supplyAsync(() => {
      val toolingService: WeaveDocumentToolingService = openDocument(position.getTextDocument)
      val offset: Int = toolingService.offsetOf(position.getPosition.getLine, position.getPosition.getCharacter)
      val suggestionResult = toolingService.completion(offset)
      val result = new util.ArrayList[CompletionItem]()
      var i = 0

      val suggestions: Array[Suggestion] = suggestionResult.suggestions
      suggestions.foreach((sug) => {
        val index: String = i.toString
        val prefix: String = "0" * (suggestionResult.suggestions.length - index.length)
        val item: CompletionItem = new CompletionItem(sug.name)
        item.setDetail(sug.wtype.map((wt) => {
          val emitterConfig = WeaveTypeEmitterConfig(prettyPrint = false, skipAnnotations = true, nameOnly = true, generateMultiTypes = false, useLiteralType = false)
          wt.toString(emitterConfig)
        }).orNull)
        item.setSortText(prefix + index)
        item.setDocumentation(new MarkupContent("markdown", sug.markdownDocumentation().getOrElse("")))
        item.setInsertText(sug.template.toVSCodeString)
        item.setInsertTextFormat(InsertTextFormat.Snippet)
        item.setKind(getCompletionType(sug))
        result.add(item)
        i = i + 1
      })
      messages.Either.forRight(new CompletionList(false, result))
    }, executor)
  }

  override def resolveCompletionItem(unresolved: CompletionItem): CompletableFuture[CompletionItem] = {
    CompletableFuture.completedFuture(unresolved)
  }

  override def hover(params: HoverParams): CompletableFuture[Hover] = {
    CompletableFuture.supplyAsync(() => {
      val toolingService: WeaveDocumentToolingService = openDocument(params.getTextDocument)
      val position = params.getPosition
      val offset: Int = toolingService.offsetOf(position.getLine, position.getCharacter)
      toolingService.hoverResult(offset)
        .map((hm) => {
          val hoverResult = new Hover()
          val expressionType: String = Option(hm.resultType).map((wt) => "Type: `" + wt.toString(prettyPrint = false, namesOnly = true, useLiterals = false) + "`\n").getOrElse("")
          val documentation: String = expressionType + "\n" + hm.markdownDocs.getOrElse("")
          hoverResult.setContents(new MarkupContent("markdown", documentation))
          hoverResult.setRange(toRange(hm.weaveLocation))
          hoverResult
        })
        .orElse({
          Option(toolingService.typeOf(offset)).map((wt) => {
            val hoverResult = new Hover()
            val expressionType = "Type: `" + wt.toString(prettyPrint = false, namesOnly = true, useLiterals = false)
            hoverResult.setContents(new MarkupContent("markdown", expressionType))
            if (wt.location().startPosition.index >= 0) {
              hoverResult.setRange(toRange(wt.location()))
            }
            hoverResult
          })
        })
        .orNull
    }, executor)
  }

  def toSymbolKind(kind: Int): SymbolKind = {
    kind match {
      case WeaveSymbolKind.Array => SymbolKind.Array
      case WeaveSymbolKind.Boolean => SymbolKind.Boolean
      case WeaveSymbolKind.Class => SymbolKind.Class
      case WeaveSymbolKind.Constant => SymbolKind.Constant
      case WeaveSymbolKind.Field => SymbolKind.Field
      case WeaveSymbolKind.Module => SymbolKind.Module
      case WeaveSymbolKind.Property => SymbolKind.Property
      case WeaveSymbolKind.Namespace => SymbolKind.Namespace
      case WeaveSymbolKind.String => SymbolKind.String
      case WeaveSymbolKind.Variable => SymbolKind.Variable
      case WeaveSymbolKind.Constructor => SymbolKind.Constructor
      case WeaveSymbolKind.Enum => SymbolKind.Enum
      case WeaveSymbolKind.Method => SymbolKind.Method
      case WeaveSymbolKind.Function => SymbolKind.Function
      case WeaveSymbolKind.File => SymbolKind.File
      case WeaveSymbolKind.Package => SymbolKind.Package
      case WeaveSymbolKind.Interface => SymbolKind.Interface
      case _ => SymbolKind.Property
    }
  }

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = {
    CompletableFuture.supplyAsync(() => {
      val document = params.getTextDocument
      val toolingService: WeaveDocumentToolingService = openDocument(document)
      val result = new util.ArrayList[messages.Either[SymbolInformation, DocumentSymbol]]()
      toolingService.documentSymbol().foreach((e) => {
        val symbol = new DocumentSymbol()
        symbol.setName(e.name)
        symbol.setSelectionRange(toRange(e.location))
        symbol.setRange(toRange(e.location))
        symbol.setKind(toSymbolKind(e.kind))
        result.add(messages.Either.forRight(symbol))
      })
      result
    }, executor)
  }

  private def openDocument(document: TextDocumentIdentifier): WeaveDocumentToolingService = {
    val uri = document.getUri
    openDocument(uri)
  }

  private def openDocument(uri: String): WeaveDocumentToolingService = {
    dwTextDocumentService.open(uri, ImplicitInput(), None)
  }

  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = {
    CompletableFuture.supplyAsync(() => {
      val toolingService: WeaveDocumentToolingService = openDocument(params.getTextDocument)
      val offset: Int = toolingService.offsetOf(params.getPosition.getLine, params.getPosition.getCharacter)
      val result = new util.ArrayList[LocationLink]()
      toolingService.definitionLink(offset).foreach((ll) => {
        val link = new LocationLink()
        link.setOriginSelectionRange(toRange(ll.linkLocation.location()))
        val reference = ll.reference
        link.setTargetRange(toRange(reference.referencedNode.location()))
        link.setTargetSelectionRange(toRange(reference.referencedNode.location()))
        if (reference.isLocalReference) {
          link.setTargetUri(params.getTextDocument.getUri)
        } else {
          //Cross module link
          val resourceResolver = projectFS.asResourceResolver
          val moduleName = reference.moduleSource.get
          resourceResolver.resolve(moduleName) match {
            case Some(value) => {
              link.setTargetUri(value.url())
            }
            case None => {
              println("Resource not found for " + moduleName)
            }
          }
        }
        result.add(link)
      })
      messages.Either.forRight(result)
    }, executor)
  }

  override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = {
    CompletableFuture.supplyAsync(() => {
      val toolingService = openDocument(params.getTextDocument)
      val offset: Int = toolingService.offsetOf(params.getPosition.getLine, params.getPosition.getCharacter)
      val ref: Array[Reference] = toolingService.rename(offset, params.getNewName)
      val edit = new WorkspaceEdit()
      val localChanges = new util.ArrayList[TextEdit]
      ref.foreach((r) => {
        localChanges.add(new TextEdit(toRange(r.referencedNode.location()), params.getNewName))
      })
      edit.getChanges.put(params.getTextDocument.getUri, localChanges)
      edit
    }, executor)
  }


  override def references(params: ReferenceParams): CompletableFuture[util.List[_ <: Location]] = {
    CompletableFuture.supplyAsync(() => {
      val toolingService = openDocument(params.getTextDocument)
      val offset: Int = toolingService.offsetOf(params.getPosition.getLine, params.getPosition.getCharacter)
      val referencesResult = toolingService.references(offset)
      JavaConverters.seqAsJavaList(
        referencesResult.map((r) => {
          val location = new Location()
          location.setRange(toRange(r.referencedNode.location()))
          location.setUri(params.getTextDocument.getUri)
          location
        })
      )
    }, executor)
  }

  private def getCompletionType(sug: Suggestion): CompletionItemKind = {
    sug.itemType match {
      case SuggestionType.Class => CompletionItemKind.Class
      case SuggestionType.Constructor => CompletionItemKind.Constructor
      case SuggestionType.Field => CompletionItemKind.Field
      case SuggestionType.Enum => CompletionItemKind.Enum
      case SuggestionType.Function => CompletionItemKind.Function
      case SuggestionType.Keyword => CompletionItemKind.Keyword
      case SuggestionType.Module => CompletionItemKind.Module
      case SuggestionType.Method => CompletionItemKind.Method
      case SuggestionType.Property => CompletionItemKind.Property
      case SuggestionType.Variable => CompletionItemKind.Variable
      case _ => CompletionItemKind.Property
    }
  }


  override def didChange(params: DidChangeTextDocumentParams): Unit = {
    val textDocument = params.getTextDocument
    println("[DataWeave] didChange : " + textDocument.getUri)
    projectFS.update(textDocument.getUri, params.getContentChanges.get(0).getText)
  }

  override def didClose(params: DidCloseTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    println("[DataWeave] didClose : " + uri)
    projectFS.close(uri)
    dwTextDocumentService.close(uri)
  }

  override def didSave(params: DidSaveTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    projectFS.save(params.getTextDocument.getUri)
    println("[DataWeave] didSave : " + uri)
  }
}
