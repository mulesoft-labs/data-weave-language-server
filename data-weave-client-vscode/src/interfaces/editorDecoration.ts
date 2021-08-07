import {RequestType} from "vscode-languageserver-protocol";


export namespace SetEditorDecorations {
  export const type = new RequestType<
    EditorDecorationParams,
    void,
    void
  >("weave/decorations/set");
}

export namespace ClearEditorDecorations {
  export const type = new RequestType<
      void,
      void,
      void
      >("weave/decorations/clear");
}

export interface EditorDecorationParams {
  documentUri: string;
  decorations: EditorDecoration[];
}

export interface EditorDecoration {
  range: EditorRange;
  text: string;
}

export interface EditorRange {
  start: EditorPosition;
  end: EditorPosition;
}

export interface EditorPosition {
  line: number;
  column: number;
}