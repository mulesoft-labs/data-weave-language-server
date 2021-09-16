package org.mule.weave.lsp.commands

import org.eclipse.lsp4j.ExecuteCommandParams
import org.mule.weave.lsp.extension.client.LaunchConfiguration
import org.mule.weave.lsp.extension.client.LaunchConfiguration._
import org.mule.weave.lsp.extension.client.LaunchConfigurationProperty
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.utils.Icons
import org.mule.weave.v2.editor.VirtualFileSystem

import java.util

class LaunchWeaveTestCommand(languageClient: WeaveLanguageClient, vfs: VirtualFileSystem) extends WeaveCommand {

  val icon: Icons = Icons.vscode

  override def commandId(): String = Commands.DW_LAUNCH_TEST

  override def execute(params: ExecuteCommandParams): AnyRef = {
    val urls: String = Commands.argAsString(params.getArguments(), 0)
    val names = urls.split(",")
      .flatMap((url) => Option(vfs.file(url)))
      .map((vf) => vf.getNameIdentifier.toString())
      .mkString(",")
    val noDebug: Boolean = Commands.argAsBoolean(params.getArguments(), 1)
    val launchConfiguration = createDefaultConfiguration(names, LaunchConfiguration.WTF_CONFIG_TYPE_NAME, noDebug)
    languageClient.runConfiguration(launchConfiguration)
    null
  }

  private def createDefaultConfiguration(mappingName: String, configType: String, noDebug: Boolean): LaunchConfiguration = {
    val mapping: LaunchConfigurationProperty = LaunchConfigurationProperty(MAIN_FILE_NAME, mappingName)
    LaunchConfiguration(configType, "Debugging " + mappingName, LAUNCH_REQUEST_TYPE, noDebug, util.Arrays.asList(mapping))
  }
}


