package org.mule.weave.lsp.weave

import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWithUnitTestsWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

class TestsDiscoveryTest extends FreeSpec {

  "Maven Project with Tests" - {
    "should retrieve the right weave items tests" in {
      val project: DWProject = getMavenProjectWithUnitTestsWorkspace()
      project.waitForProjectInitialized()
      val items = project.tests().sortBy(_.label)
      assert(items.nonEmpty)
      assert(items.length == 4)

      val item = items(0)
      assert(item.label.equals("ComplexTest"))
      assert(item.children.size().equals(1))
      assert(item.children.get(0).children.size().equals(3))
      val secondItem = items(1)
      assert(secondItem.label.equals("MoreTest"))
      assert(secondItem.children.size().equals(1))
      val thirdItem = items(2)
      assert(thirdItem.label.equals("SplitTest"))
      assert(thirdItem.children.size().equals(1))
      assert(thirdItem.children.get(0).children.size().equals(3))
      val fourthItem = items(3)
      assert(fourthItem.label.equals("UnitTest"))
      assert(fourthItem.children.size().equals(1))
    }
  }
}
