package org.mule.weave.lsp.extension.client

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient

import java.util
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

  /**
    * This notification is sent from the server to the client to execute the specified configuration on client side.
    *
    */
  @JsonNotification("weave/workspace/run")
  def runConfiguration(config: LaunchConfiguration): Unit

  /**
    * This notification is sent from the server to the client to open an editor with the specified document uri
    *
    * @param params The document to be opened
    */
  @JsonNotification("weave/workspace/openTextDocument")
  def openTextDocument(params: OpenTextDocumentParams): Unit

  /**
    * This notification is sent from the server to the client to show the live data of a script
    *
    * @param result The result of executing a script
    */
  @JsonNotification("weave/workspace/showPreviewResult")
  def showPreviewResult(result: PreviewResult): Unit

  /**
    * This notification is sent from the server to the client to publish all the resolved dependencies of this workspace
    *
    * @param resolvedDependency The list of all the resolved dependencies
    */
  @JsonNotification("weave/workspace/publishDependencies")
  def publishDependencies(resolvedDependency: DependenciesParams)
}

case class DependenciesParams(
                               //The list of dependencies
                               dependencies: util.List[DependencyDefinition]
                             )

case class DependencyDefinition(
                                 // The id of this dependency
                                 id: String,
                                 // The uri where this dependency can be located
                                 uri: String
                               )


case class PreviewResult(
                          uri: String,
                          success: Boolean,
                          logs: java.util.List[String],
                          content: String = null,
                          mimeType: String = null,
                          errorMessage: String = null,
                          timeTaken: Long = 0
                        )


case class LaunchConfiguration(
                                //The type of config
                                `type`: String,
                                //The name of the config
                                name: String,
                                //launch, attach
                                request: String,
                                //If true it will no execute in debug mode
                                noDebug: Boolean,
                                //The additional properties used for this configuration
                                properties: java.util.List[LaunchConfigurationProperty])


object LaunchConfiguration {
  val DATA_WEAVE_CONFIG_TYPE_NAME = "data-weave"
  val WTF_CONFIG_TYPE_NAME = "data-weave-testing"
  val BAT_CONFIG_TYPE_NAME = "bat"
  val WITF_CONFIG_TYPE_NAME = "data-weave-integration-testing"

  val TYPE_PROP_NAME = "type"
  val REQUEST_PROP_NAME = "request"
  val NAME_PROP_NAME = "name"
  val MAIN_FILE_NAME = "mainFile"
  val BUILD_BEFORE_PROP_NAME = "buildBefore"
  val LAUNCH_REQUEST_TYPE = "launch"

  val DEFAULT_CONFIG_NAMES: util.List[String] = util.Arrays.asList(TYPE_PROP_NAME, REQUEST_PROP_NAME, NAME_PROP_NAME)
}

case class LaunchConfigurationProperty(
                                        //The name of the property
                                        name: String,
                                        //The value of the property String, Boolean Number
                                        value: String
                                      )


case class OpenTextDocumentParams(uri: String)


case class WeaveInputBoxParams(
                                //Set title of the input box window.
                                @Nullable title: String = null,
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
                                @Nullable valueSelection: Array[Int] = null,
                                // Set list a custom buttons to appear on the quick pick.
                                @Nullable buttons: java.util.List[WeaveButton] = null,
                                //Set number if you are doing a step by step wizard.
                                @Nullable step: java.lang.Integer = null,
                                //Set number of total steps if you are doing a step by step wizard.
                                @Nullable totalSteps: java.lang.Integer = null
                              )

case class WeaveInputBoxResult(
                                // value=null when cancelled=true
                                @Nullable value: String = null,
                                @Nullable cancelled: java.lang.Boolean = null,
                                @Nullable buttonPressedId: String = null
                              )

case class WeaveQuickPickParams(
                                 items: java.util.List[WeaveQuickPickItem],
                                 //Set title of the quick pick window.
                                 @Nullable title: String = null,
                                 // An optional flag to include the description when filtering the picks.
                                 @Nullable matchOnDescription: java.lang.Boolean = null,
                                 // An optional flag to include the detail when filtering the picks.
                                 @Nullable matchOnDetail: java.lang.Boolean = null,
                                 // An optional string to show as place holder in the input box to guide the user what to pick on.
                                 @Nullable placeHolder: String = null,
                                 // Set to `true` to keep the picker open when focus moves to another part of the editor or to another window.
                                 @Nullable ignoreFocusOut: java.lang.Boolean = null,
                                 // Set list a custom buttons to appear on the quick pick.
                                 @Nullable buttons: java.util.List[WeaveButton] = null,
                                 // Set to `true` to allow user to select many of the options.
                                 @Nullable canSelectMany: java.lang.Boolean = null,
                                 //Set step number if you are doing a step by step wizard.
                                 @Nullable step: java.lang.Integer = null,
                                 //Set number of total steps if you are doing a step by step wizard.
                                 @Nullable totalSteps: java.lang.Integer = null,
                               )

case class WeaveQuickPickResult(
                                 // value=null when cancelled=true
                                 @Nullable buttonPressedId: String = null,
                                 @Nullable itemsId: java.util.List[String] = null,
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
                               @Nullable alwaysShow: java.lang.Boolean = null,
                               // Set to true if this option is picked by default (only if it's a multiplepick)
                               @Nullable picked: java.lang.Boolean = null
                             )

case class OpenWindowsParams(
                              uri: String,
                              openNewWindow: java.lang.Boolean
                            )

case class WeaveButton(
                        id: String,
                        iconPath: ThemeIconPath,
                        @Nullable tooltip: String = null
                      )

sealed trait ThemeIconPath {
  def iconType: String
}

//Icon Uris for dark and light theme only.
case class DarkLightIcon(dark: String,
                         light: String, override val iconType: String = "DARK-LIGHT-ICON") extends ThemeIconPath

//Unified Icon for every theme URI
case class IconUri(uri: String, override val iconType: String = "URI-ICON") extends ThemeIconPath

//Icon provided by the client side matched by id.
case class ThemeIcon(id: String, override val iconType: String = "THEME-ICON") extends ThemeIconPath
