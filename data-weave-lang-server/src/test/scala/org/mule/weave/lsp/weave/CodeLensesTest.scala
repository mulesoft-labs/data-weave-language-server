package org.mule.weave.lsp.weave

import org.eclipse.lsp4j.CodeLens
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.mule.weave.lsp.commands.Commands
import org.scalatest.FreeSpec

import java.util.Optional

class CodeLensesTest extends FreeSpec {

  "Maven Project" - {
    "should return lenses for sample data and run mapping" in {
      val project: DWProject = getMavenProjectWorkspace()
      project.waitForProjectInitialized()
      val lenses = project.codeLenses("src/main/dw/MyMapping.dwl")
      assert(lenses.size() == 2)

      val launchLens: Optional[_ <: CodeLens] = lenses.stream().filter((lense) => {
        lense.getCommand.getCommand == Commands.DW_LAUNCH_MAPPING
      }).findFirst()
      assert(launchLens.isPresent)
      assert(launchLens.get().getCommand.getArguments.size() == 3)
      assert(launchLens.get().getCommand.getArguments.get(0) == "MyMapping")
      assert(launchLens.get().getCommand.getArguments.get(1) == "data-weave")
      assert(launchLens.get().getCommand.getArguments.get(2) == "false")

      val sampleData: Optional[_ <: CodeLens] = lenses.stream().filter((lense) => {
        lense.getCommand.getCommand == Commands.DW_CREATE_SCENARIO
      }).findFirst()
      assert(sampleData.isPresent)
    }
  }
}
