package org.mule.weave.lsp.extension.services

import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.services.TextDocumentService

@JsonSegment("textDocument")
trait FocusAwareDocumentService {

  /** *
    * Notification sent from the client to the server when there is a focus changed into an specific file.
    *
    * @param params document identifier with the file URI
    */
  @JsonNotification
  def didFocusChange(params: DidFocusChangeParams);

}

case class DidFocusChangeParams(textDocumentIdentifier: TextDocumentIdentifier)

trait WeaveTextDocumentService extends TextDocumentService with FocusAwareDocumentService
