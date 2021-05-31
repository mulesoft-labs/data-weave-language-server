package org.mule.weave.lsp.weave

import org.mule.weave.lsp.ClientUI
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWithSamplesWorkspace
import org.mule.weave.lsp.DWProject
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.extension.client.WeaveQuickPickResult
import org.mule.weave.lsp.commands.Commands
import org.scalatest.FreeSpec

import java.io.File
import java.io.File.separator

class CreateSampleCommandTest extends FreeSpec {

  val MAPPING_NAME = "MyMapping"
  val SCENARIO_NAME = "Basic_Scenario"

  "should create sample folder correctly for payload" in {
    val project: DWProject = getMavenProjectWithSamplesWorkspace()
    var i = 0
    project.withClientUI(new ClientUI {
      override def weaveInputBox(params: WeaveInputBoxParams): WeaveInputBoxResult = {
        try {
          i match {
            case 0 => WeaveInputBoxResult(value = "Basic Scenario")
            case _ => WeaveInputBoxResult(value = "payload.json")
          }
        } finally {
          i = i + 1
        }
      }
      override def weaveQuickPick(params: WeaveQuickPickParams): WeaveQuickPickResult = ???
    })

    project.runCommand(Commands.DW_DEFINE_SAMPLE_DATA, MAPPING_NAME)
    val workspaceRoot = project.workspaceRoot.toFile

    val scenario = new File(workspaceRoot, s"src${separator}test${separator}dwit${separator}$MAPPING_NAME${separator}$SCENARIO_NAME")
    assert(scenario.exists())
    val payload = new File(new File(scenario, "inputs"), "payload.json")
    assert(payload.exists())
  }

  "should create sample folder correctly for variables" in {
    val project: DWProject = getMavenProjectWithSamplesWorkspace()
    var i = 0
    project.withClientUI(new ClientUI {
      override def weaveInputBox(params: WeaveInputBoxParams): WeaveInputBoxResult = {
        try {
          i match {
            case 0 => WeaveInputBoxResult(value = "Basic Scenario")
            case _ => WeaveInputBoxResult(value = "vars.user.json")
          }
        } finally {
          i = i + 1
        }
      }

      override def weaveQuickPick(params: WeaveQuickPickParams): WeaveQuickPickResult = ???
    })

    project.runCommand(Commands.DW_DEFINE_SAMPLE_DATA, MAPPING_NAME)
    val workspaceRoot = project.workspaceRoot.toFile

    val scenario = new File(workspaceRoot, s"src${separator}test${separator}dwit${separator}$MAPPING_NAME${separator}$SCENARIO_NAME")
    assert(scenario.exists())
    val payload = new File(new File(scenario, s"inputs${separator}vars"), "user.json")
    assert(payload.exists())
  }

}
