import { NotificationType, RequestType } from "vscode-languageserver-protocol";

export namespace OpenFolder {
    export const type = new NotificationType<
    OpenFolderParams,
      void
    >("weave/folder/open");

    export interface OpenFolderParams{
        uri: string,
        openNewWindow: boolean
    }
}