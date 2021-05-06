import { RequestType } from "vscode-languageserver-protocol";

export namespace WeaveQuickPick {
  export const type = new RequestType<
    WeaveQuickPickParams,
    WeaveQuickPickResult,
    void
  >("weave/quickPick");
}

export interface WeaveQuickPickParams {
  /** An array of items that can be selected from. */
  items: WeaveQuickPickItem[];
  /** An optional flag to include the description when filtering the picks. */
  matchOnDescription?: boolean;
  /** An optional flag to include the detail when filtering the picks. */
  matchOnDetail?: boolean;
  /**
   * An optional string to show as place holder in the input box to guide the
   * user what to pick on.
   */
  placeHolder?: string;
  /**
   * Set to `true` to keep the picker open when focus moves to another part of
   * the editor or to another window.
   */
  ignoreFocusOut?: boolean;
}

export interface WeaveQuickPickResult {
  itemId?: string;
  cancelled?: boolean;
}

export interface WeaveQuickPickItem {
  /** An id for this items that should be return as a result of the picking. */
  id: string;
  /** A human readable string which is rendered prominent. */
  label: string;
  /** A human readable string which is rendered less prominent. */
  description?: string;
  /** A human readable string which is rendered less prominent. */
  detail?: string;
  /** Always show this item. */
  alwaysShow?: boolean;
}