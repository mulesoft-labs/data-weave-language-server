package org.mule.weave.lsp.weave

import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWithUnitTestsWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

class TestsDiscoveryTest extends FreeSpec {

  "Maven Project with Tests" - {
    "should retrieve the right weave items tests" in {
      val project: DWProject = getMavenProjectWithUnitTestsWorkspace()
      project.waitForProjectInitialized()
      val items = project.tests()
      assert(items.nonEmpty)
      assert(items.length == 2)
      val item = items.head
      assert(item.label.equals("FirstTestSuite"))
      assert(item.children.size().equals(1))
      val secondItem = items(1)
      assert(secondItem.label.equals("SecondTestSuite"))
      assert(secondItem.children.size().equals(1))
    }
  }
}
