package org.mule.weave.lsp.weave

import org.eclipse.lsp4j.LocationLink
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

import java.util

class DefinitionsTest extends FreeSpec {

  "Maven Project" - {
    "should navigate to definition correctly local references" in {
      val project: DWProject = getMavenProjectWorkspace()
      val definitions: util.List[_ <: LocationLink] = project.definition("src/main/dw/FunctionReference.dwl", 3, 1)
      assert(definitions.size() == 1)
      assert(definitions.get(0).getTargetUri.contains("src/main/dw/FunctionReference.dwl"))
      assert(definitions.get(0).getTargetRange.getStart.getLine == 1)
    }


    "should navigate to definition correctly cross references" in {
      val project: DWProject = getMavenProjectWorkspace()
      val definitions: util.List[_ <: LocationLink] = project.definition("src/main/dw/MappingUsingMyLib.dwl", 2, 1)
      assert(definitions.size() == 1)
      assert(definitions.get(0).getTargetUri.contains("src/main/dw/MyLib.dwl"))
      assert(definitions.get(0).getTargetRange.getStart.getLine == 0)
    }
  }

}
