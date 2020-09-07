package org.mule.weave.lsp.vfs

import java.io.File

import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.DefaultWeaveResource
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

/**
 * This weave resource resolver works for relative to
 *
 * @param root The root folder
 * @param vfs  The virtual file system that creates this
 */
class FolderWeaveResourceResolver(root: File, vfs: VirtualFileSystem) extends WeaveResourceResolver {


  override def resolvePath(path: String): Option[WeaveResource] = {
    val theFile = new File(root, path)
    fileToResources(theFile)
  }

  override def resolveAll(name: NameIdentifier): Seq[WeaveResource] = {
    super.resolveAll(name)
  }

  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path = NameIdentifierHelper.toWeaveFilePath(name)
    val theFile = new File(root, path)
    fileToResources(theFile)
  }

  private def fileToResources(theFile: File): Option[DefaultWeaveResource] = {
    val vsCodeUrlStyle = FileUtils.toUrl(theFile)
    Option(vfs.file(vsCodeUrlStyle)).map((vf) => {
      WeaveResource(vf.path(), vf.read())
    })
  }
}