import { QuickInputButton, ThemeColor, ThemeIcon, Uri } from "vscode";
import { RequestType } from "vscode-languageserver-protocol";

export namespace IconType {
  export const URIIconType = "URI-ICON"
  export const ThemeIcontType = "THEME-ICON"
  export const DarkLightIconPathType = "DARK-LIGHT-ICON"
}

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

  //Set step number if you are doing a step by step wizard.
  step?: number;

  //Set number of total steps if you are doing a step by step wizard.
  totalSteps?: number;

  //Set title of the quick pick window.
  title?: string;

  //Buttons to show in the quick pick.
  buttons?: WeaveInputButton[];

  // Set to `true` to allow user to select many of the options.
  canSelectMeny?: boolean;

}

export interface IconPath {
  iconType: string
}

export interface DarkLightIconPath extends IconPath {
  dark: string
  light: string
}

export interface IconUri extends IconPath {
  uri: string
}

export interface ThemeIconPath extends IconPath {
  id: string
}

export interface WeaveInputButton {
  id: string;
  iconPath: IconPath;
  tooltip?: string;
}
export interface WeaveQuickPickResult {
  buttonPressedId?: string;
  itemsId?: Array<string>;
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