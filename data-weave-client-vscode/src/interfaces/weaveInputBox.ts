import { QuickInputButton } from "vscode";
import { RequestType } from "vscode-languageserver-protocol";
import { WeaveInputButton } from "./weaveQuickPick";

export namespace WeaveInputBox {
  export const type = new RequestType<
    InputBoxOptions,
    WeaveInputBoxResult,
    void
  >("weave/inputBox");

  export const handleInput = function (
    result: string | undefined
  ): WeaveInputBoxResult {
    if (result === undefined || result.trim() === "") {
      return { cancelled: true };
    } else {
      return { value: result };
    }
  };
}

export interface WeaveInputBoxResult {
  buttonPressedId?: string;
  value?: string;
  cancelled?: boolean;
}

export interface InputBoxOptions {
  /** * The value to prefill in the input box.  */
  value?: string;
  /** * Selection of the prefilled [`value`](#InputBoxOptions.value). Defined as tuple of two number where the * first is the inclusive start index and the second the exclusive end index. When `undefined` the whole * word will be selected, when empty (start equals end) only the cursor will be set, * otherwise the defined range will be selected.  */
  valueSelection?: [
    number,
    number
  ];

  /**
   * The text to display underneath the input box.
   */
  prompt?: string;

  /**
   * An optional string to show as place holder in the input box to guide the user what to type.
   */
  placeHolder?: string;

  /**
   * Set to `true` to show a password prompt that will not show the typed value.
   */
  password?: boolean;

  /**
   * Set to `true` to keep the input box open when focus moves to another part of the editor or to another window.
   */
  ignoreFocusOut?: boolean;


  //Set step number if you are doing a step by step wizard.
  step?: number;

  //Set number of total steps if you are doing a step by step wizard.
  totalSteps?: number;

  //Set title of the quick pick window.
  title?: string;

  //Buttons to show in the quick pick.
  buttons?: Array<WeaveInputButton>;

  /**
   * An optional function that will be called to validate input and to give a hint
   * to the user.
   *
   * @param value The current value of the input box.
   * @return A human readable string which is presented as diagnostic message.
   * Return `undefined`, `null`, or the empty string when 'value' is valid.
   */
  validateInput?(
    value: string
  ): string | undefined | null | Thenable<string | undefined | null>;
}
