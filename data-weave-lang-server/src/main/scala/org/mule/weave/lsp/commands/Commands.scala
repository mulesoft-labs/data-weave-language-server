package org.mule.weave.lsp.commands

import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive

import java.util

/**
  * List of Supported Commands
  */
object Commands {

  val BAT_RUN_BAT_TEST = "bat.runCurrentBatTest"
  val BAT_RUN_BAT_FOLDER = "bat.runFolder"
  val BAT_INSTALL_BAT_CLI = "bat.installCli"
  val DW_LAUNCH_MAPPING = "dw.launchCommand"
  val DW_LAUNCH_TEST = "dw.launchTest"
  val DW_ENABLE_PREVIEW = "dw.enablePreview"
  val DW_RELOAD_DEPENDENCIES = "dw.reloadDependencies"
  val DW_RUN_PREVIEW = "dw.runPreview"
  val DW_RUN_MAPPING = "dw.runCommand"
  val DW_QUICK_FIX = "dw.quickFix"
  val DW_GENERATE_WEAVE_DOC = "dw.generateWeaveDoc"
  val DW_INSERT_WEAVE_TYPE = "dw.insertWeaveType"
  val DW_CREATE_SCENARIO = "dw.createScenario"
  val DW_CREATE_INPUT_SAMPLE = "dw.createInputSample"
  val DW_DELETE_INPUT_SAMPLE = "dw.deleteInputSample"
  val DW_DELETE_EXPECTED_OUTPUT = "dw.deleteExpectedOutput"
  val DW_SAVE_OUTPUT = "dw.saveOutput"
  val DW_ACTIVE_SCENARIO = "dw.activeScenario"
  val DW_EXTRACT_VARIABLE = "dw.extractVariable"
  val DW_DELETE_SCENARIO = "dw.deleteScenario"
  val DW_CREATE_TEST = "dw.createTest"
  val DW_CREATE_UNIT_TEST = "dw.createUnitTest"
  val DW_CREATE_MAPPING = "dw.createMapping"
  val DW_CREATE_MODULE = "dw.createModule"

  val ALL_COMMANDS: util.List[String] = util.Arrays.asList(
    BAT_RUN_BAT_TEST,
    BAT_RUN_BAT_FOLDER,
    BAT_INSTALL_BAT_CLI,
    DW_RUN_MAPPING,
    DW_RUN_PREVIEW,
    DW_SAVE_OUTPUT,
    DW_DELETE_EXPECTED_OUTPUT,
    DW_ENABLE_PREVIEW,
    DW_RELOAD_DEPENDENCIES,
    DW_LAUNCH_MAPPING,
    DW_LAUNCH_TEST,
    DW_QUICK_FIX,
    DW_CREATE_SCENARIO,
    DW_DELETE_SCENARIO,
    DW_ACTIVE_SCENARIO,
    DW_CREATE_INPUT_SAMPLE,
    DW_DELETE_INPUT_SAMPLE,
    DW_GENERATE_WEAVE_DOC,
    DW_CREATE_TEST,
    DW_INSERT_WEAVE_TYPE,
    DW_CREATE_MAPPING,
    DW_CREATE_MODULE,
    DW_EXTRACT_VARIABLE,
    DW_CREATE_UNIT_TEST,
  )

  def optionalArgAsString(arguments: util.List[AnyRef], index: Int): Option[String] = {
    if (arguments.size() <= index) {
      None
    } else {
      val value = arguments.get(index)
      value match {
        case _: JsonNull => None
        case jp: JsonPrimitive => Some(jp.getAsString)
        case d => throw new RuntimeException(s"Expecting `String` but got ${d.getClass.getName}")
      }
    }
  }


  def argAsString(arguments: util.List[AnyRef], index: Int) = {
    if (arguments.size() <= index) {
      throw new RuntimeException(s"Missing argument ${index}.")
    }
    val value = arguments.get(index)
    value match {
      case _: JsonNull => null
      case jp: JsonPrimitive => jp.getAsString
      case d => throw new RuntimeException(s"Expecting `String` but got ${d.getClass.getName}")
    }

  }

  def argAsInt(arguments: util.List[AnyRef], index: Int): Int = {
    val value = arguments.get(index)
    value match {
      case jp: JsonPrimitive => jp.getAsInt
      case d => throw new RuntimeException(s"Expecting `Int` but got ${d.getClass.getName}")
    }
  }

  def argAsBoolean(arguments: util.List[AnyRef], index: Int): Boolean = {
    val value = arguments.get(index)
    value match {
      case jp: JsonPrimitive => jp.getAsBoolean
      case d => throw new RuntimeException(s"Expecting `Boolean` but got ${d.getClass.getName}")
    }
  }

}
