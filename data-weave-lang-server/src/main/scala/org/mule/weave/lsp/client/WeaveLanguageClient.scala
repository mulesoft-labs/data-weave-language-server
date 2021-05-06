package org.mule.weave.lsp.client

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient

import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable

trait WeaveLanguageClient extends LanguageClient {

  /**
    * Opens an input box to ask the user for input.
    *
    * @return the user provided input. The future can be cancelled, meaning
    *         the input box should be dismissed in the editor.
    */
  @JsonRequest("weave/inputBox")
  def weaveInputBox(params: WeaveInputBoxParams): CompletableFuture[WeaveInputBoxResult]

  /**
    * Opens an menu to ask the user to pick one of the suggested options.
    *
    * @return the user provided pick. The future can be cancelled, meaning
    *         the input box should be dismissed in the editor.
    */
  @JsonRequest("weave/quickPick")
  def weaveQuickPick(params: WeaveQuickPickParams): CompletableFuture[WeaveQuickPickResult]



  @JsonNotification("weave/folder/open")
  def openWindow(params: OpenWindowsParams): Unit
}

case class WeaveInputBoxParams(
                                // The value to prefill in the input box
                                @Nullable value: String = null,
                                // The text to display underneath the input box.
                                @Nullable prompt: String = null,
                                // An optional string to show as place holder in the input box to guide the user what to type.
                                @Nullable placeholder: String = null,
                                // Set to `true` to show a password prompt that will not show the typed value.
                                @Nullable password: java.lang.Boolean = null,
                                // Set to `true` to keep the input box open when focus moves to another
                                // part of the editor or to another window.
                                @Nullable ignoreFocusOut: java.lang.Boolean = null,
                                @Nullable valueSelection: Array[Int] = null
                              )

case class WeaveInputBoxResult(
                                // value=null when cancelled=true
                                @Nullable value: String = null,
                                @Nullable cancelled: java.lang.Boolean = null
                              )

case class WeaveQuickPickParams(
                                 items: java.util.List[WeaveQuickPickItem],
                                 // An optional flag to include the description when filtering the picks.
                                 @Nullable matchOnDescription: java.lang.Boolean = null,
                                 // An optional flag to include the detail when filtering the picks.
                                 @Nullable matchOnDetail: java.lang.Boolean = null,
                                 // An optional string to show as place holder in the input box to guide the user what to pick on.
                                 @Nullable placeHolder: String = null,
                                 // Set to `true` to keep the picker open when focus moves to another part of the editor or to another window.
                                 @Nullable ignoreFocusOut: java.lang.Boolean = null
                               )

case class WeaveQuickPickResult(
                                 // value=null when cancelled=true
                                 @Nullable itemId: String = null,
                                 @Nullable cancelled: java.lang.Boolean = null
                               )

case class WeaveQuickPickItem(
                               id: String,
                               // A human readable string which is rendered prominent.
                               label: String,
                               // A human readable string which is rendered less prominent.
                               @Nullable description: String = null,
                               // A human readable string which is rendered less prominent.
                               @Nullable detail: String = null,
                               // Always show this item.
                               @Nullable alwaysShow: java.lang.Boolean = null
                             )

case class OpenWindowsParams(
                                   uri: String,
                                   openNewWindow: java.lang.Boolean
                                 )


