package org.mule.weave.lsp.weave

import org.mule.weave.lsp.ClientUI
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWithSamplesWorkspace
import org.mule.weave.lsp.DWProject
import org.mule.weave.lsp.commands.Commands
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.extension.client.WeaveQuickPickResult
import org.mule.weave.lsp.utils.URLUtils
import org.scalatest.FreeSpec

import java.io.File
import java.io.File.separator

class DeleteInputCommandTest extends FreeSpec {

  val MAPPING_NAME = "org::MyMapping"
  val MAPPING_FOLDER_NAME = "org-MyMapping"
  val SCENARIO_FOLDER_NAME = "Basic Scenario"
  val SCENARIO_NAME = "Basic Scenario"

  "should create an scenario correctly for a given mapping" in {
    val project: DWProject = getMavenProjectWithSamplesWorkspace()
    var i = 0
    project.withClientUI(new ClientUI {
      override def weaveInputBox(params: WeaveInputBoxParams): WeaveInputBoxResult = {
        try {
          i match {
            case 0 => WeaveInputBoxResult(value = SCENARIO_NAME)
            case 1 => WeaveInputBoxResult(value = "payload.json")
            // This is the name of the new input
            case 2 => WeaveInputBoxResult(value = "vars.test.json")
          }
        } finally {
          i = i + 1
        }
      }

      override def weaveQuickPick(params: WeaveQuickPickParams): WeaveQuickPickResult = ???
    })

    project.runCommand(Commands.DW_CREATE_SCENARIO, MAPPING_NAME)
    val workspaceRoot = project.workspaceRoot.toFile
    val scenario = new File(workspaceRoot, s"src${separator}test${separator}dwit${separator}$MAPPING_FOLDER_NAME${separator}$SCENARIO_FOLDER_NAME")
    assert(scenario.exists())

    project.runCommand(Commands.DW_CREATE_INPUT_SAMPLE, MAPPING_NAME, SCENARIO_NAME)
    val input = new File(scenario, s"inputs${separator}vars${separator}test.json")
    assert(input.exists())

    project.runCommand(Commands.DW_DELETE_INPUT_SAMPLE, MAPPING_NAME, SCENARIO_NAME, URLUtils.toLSPUrl(input))

    assert(!input.exists())

  }

}
