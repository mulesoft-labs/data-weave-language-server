import { NotificationType, RequestType } from "vscode-languageserver-protocol";

export namespace OpenTextDocument {
    export const type = new NotificationType<
    OpenTextDocumentParams,
      void
    >("weave/workspace/openTextDocument");

    export interface OpenTextDocumentParams{
        uri: string;
        startLine?: number;
        startCharacter?: number;
        endLine?: number;
        endCharacter?: number;
    }
}