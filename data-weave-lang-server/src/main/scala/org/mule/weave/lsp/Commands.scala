package org.mule.weave.lsp

import java.util

object Commands {
  val BAT_RUN_BAT_TEST = "bat.runCurrentBatTest"
  val BAT_RUN_BAT_FOLDER = "bat.runFolder"
  val BAT_INSTALL_BAT_CLI = "bat.installCli"
  val DW_RUN_DEBUGGER = "dw.launchDebuggerServerAdapter"

  val ALL_COMMANDS = util.Arrays.asList(BAT_RUN_BAT_TEST, Commands.BAT_RUN_BAT_FOLDER, Commands.BAT_INSTALL_BAT_CLI, Commands.DW_RUN_DEBUGGER )

}
