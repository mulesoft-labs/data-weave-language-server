import { Range } from "vscode";
import {NotificationType, RequestType} from "vscode-languageserver-protocol";


export namespace SetEditorDecorations {
  export const type = new NotificationType<
    EditorDecorationParams,    
    void
  >("weave/decorations/set");
}

export namespace ClearEditorDecorations {
  export const type = new NotificationType<
      void,
      void
      >("weave/decorations/clear");
}

export interface EditorDecorationParams {
  documentUri: string;
  decorations: EditorDecoration[];
}

export interface EditorDecoration {
  range: Range;
  text: string;
}
