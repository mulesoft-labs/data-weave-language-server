package org.mule.weave.lsp.vfs

import org.mule.weave.v2.editor.VirtualFileSystem

/**
  * This trait is to represent Artifact File System, for example a Jar or a Zip
  */
trait ArtifactVirtualFileSystem extends VirtualFileSystem {

  /**
    * The ID of this Artifact
    *
    * @return
    */
  def artifactId(): String

}
