package org.mule.weave.lsp.weave

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.scalatest.FreeSpec

import java.util

class ReferenceTest extends FreeSpec {

  "Maven Project" - {
    "should resolve local rename" in {
      val project: DWProject = getMavenProjectWorkspace()
      val references: util.List[_ <: Location] = project.referencesOfLocalFile("src/main/dw/FunctionReference.dwl", 1, 5)
      assert(references.size() == 1)
      assert(references.get(0).getRange.getStart.getLine == 3)
    }

    "should resolve remote reference" in {
      val project: DWProject = getMavenProjectWorkspace()
      val definitions: util.List[_ <: LocationLink] = project.definition("src/main/dw/MyMapping.dwl", 2, 11)
      assert(definitions.size() >= 1)
      val firstDefinition = definitions.get(0)
      val uri = firstDefinition.getTargetUri
      val startLocation = firstDefinition.getTargetRange.getStart
      val remoteReferences = project.referencesOf(uri, startLocation.getLine, startLocation.getCharacter)
      val link = remoteReferences.stream().filter((l) => l.getUri.contains("src/main/dw/MyMapping.dwl")).findFirst()
      //Make sure there is at least one reference to the current file
      assert(link.isPresent)
      assert(link.get().getRange.getStart.getLine == 2)
    }
  }

}
