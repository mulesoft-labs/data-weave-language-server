package org.mule.weave.lsp.weave

import org.eclipse.lsp4j.Location
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

import java.util

class UsagesTest extends FreeSpec {

  "Maven Project" - {
    "should resolve local reference" in {
      val project: DWProject = getMavenProjectWorkspace()
      val references: util.List[_ <: Location] = project.reference("src/main/dw/MyMapping.dwl", 3, 6)
      println(references)
    }
  }

}
