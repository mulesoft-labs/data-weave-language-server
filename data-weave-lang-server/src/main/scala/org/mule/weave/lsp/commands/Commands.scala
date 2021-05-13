package org.mule.weave.lsp.commands

import com.google.gson.JsonPrimitive

import java.util

/**
  * List of Supported Commands
  */
object Commands {

  val BAT_RUN_BAT_TEST = "bat.runCurrentBatTest"
  val BAT_RUN_BAT_FOLDER = "bat.runFolder"
  val BAT_INSTALL_BAT_CLI = "bat.installCli"

  val DW_RUN_MAPPING = "dw.runCommand"
  val DW_QUICK_FIX = "dw.quickFix"
  val DW_GENERATE_WEAVE_DOC = "dw.generateWeaveDoc"
  val DW_INSERT_RETURN_TYPE = "dw.insertReturnType"

  val ALL_COMMANDS: util.List[String] = util.Arrays.asList(
    BAT_RUN_BAT_TEST,
    BAT_RUN_BAT_FOLDER,
    BAT_INSTALL_BAT_CLI,
    DW_RUN_MAPPING,
    DW_QUICK_FIX,
    DW_GENERATE_WEAVE_DOC,
    DW_INSERT_RETURN_TYPE
  )


  def argAsString(arguments: util.List[AnyRef], index: Int) = {
    arguments.get(index).asInstanceOf[JsonPrimitive].getAsString
  }

  def argAsInt(arguments: util.List[AnyRef], index: Int) = {
    arguments.get(index).asInstanceOf[JsonPrimitive].getAsInt
  }

  def argAsBoolean(arguments: util.List[AnyRef], index: Int) = {
    arguments.get(index).asInstanceOf[JsonPrimitive].getAsBoolean
  }

}
