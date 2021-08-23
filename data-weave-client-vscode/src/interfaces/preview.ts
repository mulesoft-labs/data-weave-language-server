import { NotificationType } from "vscode-languageserver-protocol";

export namespace ShowPreviewResult {
  export const type = new NotificationType<
    PreviewResult,
    void
  >("weave/workspace/showPreviewResult");

  export interface PreviewResult {
    uri: string,
    success: boolean,
    logs: string[],
    content: string,
    mimeType: string,
    errorMessage: string,
    timeTaken: number,
    scenarioUri: string
  }
}