package org.mule.weave.lsp.commands

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.mule.weave.lsp.extension.client.LaunchConfiguration
import org.mule.weave.lsp.extension.client.LaunchConfiguration._
import org.mule.weave.lsp.extension.client.LaunchConfigurationProperty
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickItem
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.extension.client.WeaveQuickPickResult
import org.mule.weave.lsp.utils.Icons

import java.util

class LaunchWeaveCommand(languageClient: WeaveLanguageClient) extends WeaveCommand {

  val icon: Icons = Icons.vscode

  override def commandId(): String = Commands.DW_LAUNCH_MAPPING

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val mappingName: String = Commands.argAsString(params.getArguments(), 0)
    val configType: String = Commands.argAsString(params.getArguments(), 1)

    val item = new ConfigurationItem()
    val workspaceFolders: util.List[WorkspaceFolder] = languageClient.workspaceFolders().get()

    if (workspaceFolders != null && !workspaceFolders.isEmpty) {
      item.setScopeUri(workspaceFolders.get(0).getUri)
      item.setSection(LAUNCH_REQUEST_TYPE)
      val configurations: util.List[AnyRef] = languageClient.configuration(new ConfigurationParams(util.Arrays.asList(item))).get()
      val results: util.List[LaunchConfiguration] = new util.ArrayList[LaunchConfiguration]()
      if (!configurations.isEmpty) {
        var e = 0
        while (e < configurations.size()) {
          val config = configurations.get(0)
          config match {
            case jsonObject: JsonObject => {
              val array: JsonArray = jsonObject.getAsJsonArray("configurations")
              if (array != null) {
                val configs = array.iterator()
                while (configs.hasNext) {
                  val launchConfig: JsonObject = configs.next().getAsJsonObject
                  //If we found a config of type DataWeave
                  //And points to this mapping
                  if (
                    launchConfig.get(TYPE_PROP_NAME).getAsString == configType &&
                      launchConfig.get(MAIN_FILE_NAME).getAsString == mappingName &&
                      launchConfig.get(REQUEST_PROP_NAME).getAsString == LAUNCH_REQUEST_TYPE
                  ) {
                    val entries = launchConfig.entrySet().iterator()
                    val theName = launchConfig.get(NAME_PROP_NAME).getAsString
                    val theRequest = launchConfig.get(REQUEST_PROP_NAME).getAsString
                    val configurationProperties = new util.ArrayList[LaunchConfigurationProperty]()
                    while (entries.hasNext) {
                      val entry = entries.next()
                      if (!DEFAULT_CONFIG_NAMES.contains(entry.getKey)) {
                        configurationProperties.add(LaunchConfigurationProperty(entry.getKey, entry.getValue.getAsJsonPrimitive.getAsString))
                      }
                    }
                    results.add(LaunchConfiguration(DATA_WEAVE_CONFIG_TYPE_NAME, theName, theRequest, noDebug = false, configurationProperties))
                  }
                }
              }
            }
            case _ =>
          }
          e = e + 1
        }
      }

      if (results.isEmpty) {
        val launchConfiguration = createDefaultConfiguration(mappingName, configType)
        languageClient.runConfiguration(launchConfiguration)
      } else if (results.size() == 1) {
        languageClient.runConfiguration(results.get(0))
      } else {
        val items = new util.ArrayList[WeaveQuickPickItem]()
        val configIterators = results.iterator()
        var i = 0
        while (configIterators.hasNext) {
          val configuration = configIterators.next()
          items.add(WeaveQuickPickItem(i.toString, icon.file + configuration.name))
          i = i + 1
        }
        val result: WeaveQuickPickResult = languageClient.weaveQuickPick(WeaveQuickPickParams(
          items = items,
          title = "Select The Configuration To Run"
        )).get()
        if (!result.cancelled) {
          languageClient.runConfiguration(results.get(result.itemsId.get(0).toInt))
        }
      }
    } else {
      val launchConfiguration = createDefaultConfiguration(mappingName, configType)
      languageClient.runConfiguration(launchConfiguration)
    }
    null
  }

  private def createDefaultConfiguration(mappingName: String, configType: String): LaunchConfiguration = {
    val mapping: LaunchConfigurationProperty = LaunchConfigurationProperty(MAIN_FILE_NAME, mappingName)
    LaunchConfiguration(configType, "Debugging " + mappingName, LAUNCH_REQUEST_TYPE, noDebug = false, util.Arrays.asList(mapping))
  }
}


