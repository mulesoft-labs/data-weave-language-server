import { NotificationType, TextDocumentIdentifier } from "vscode-languageserver-protocol";


export namespace TextDocument {
  export const type = new NotificationType<
    DidFocusChangeParams
  >("textDocument/didFocusChange");


  export interface DidFocusChangeParams {
    textDocumentIdentifier: TextDocumentIdentifier
  };

}