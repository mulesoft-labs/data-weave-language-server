package org.mule.weave.lsp.weave

import org.eclipse.lsp4j.TextEdit
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

import java.util

class RenameTest extends FreeSpec {

  "Maven Project" - {
    "should rename correctly local references" in {
      val project: DWProject = getMavenProjectWorkspace()
      project.waitForProjectInitialized()
      val workspaceEdit = project.rename("src/main/dw/FunctionReference.dwl", 1, 5, "myTest")
      val changes: util.Map[String, util.List[TextEdit]] = workspaceEdit.getChanges
      assert(changes.size() == 1)
      assert(changes.values().iterator().hasNext)
      val textEdits: util.List[TextEdit] = changes.values().iterator().next()
      assert(textEdits.size() == 2)
      val functionNameChange = textEdits.stream().anyMatch((te) => {
        te.getRange.getStart.getLine == 1 && te.getNewText == "myTest"
      })

      assert(functionNameChange)

      val referenceName = textEdits.stream().anyMatch((te) => {
        te.getRange.getStart.getLine == 3 && te.getNewText == "myTest"
      })

      assert(referenceName)
    }

    "should rename remote reference correctly" in {
      val project: DWProject = getMavenProjectWorkspace()
      project.waitForProjectInitialized()
      val workspaceEdit = project.rename("src/main/dw/MappingUsingMyLib.dwl", 2, 1, "myTest")
      assert(workspaceEdit.getChanges.size() == 2)
      val changeSets = workspaceEdit.getChanges.entrySet()
      val localChange = changeSets.stream().anyMatch((s) => {
        s.getKey.contains("MappingUsingMyLib") && s.getValue.size() == 1
      })
      assert(localChange)

      val remoteChange = changeSets.stream().anyMatch((s) => {
        s.getKey.contains("MyLib") && s.getValue.size() == 1
      })
      assert(remoteChange)
    }
  }

}
