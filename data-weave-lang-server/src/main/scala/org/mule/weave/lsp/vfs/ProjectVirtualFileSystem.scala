package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URL

import org.mule.weave.lsp.WeaveLanguageServer
import org.mule.weave.v2.editor.ChangeListener
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
import org.mule.weave.v2.sdk.EmptyWeaveResourceResolver
import org.mule.weave.v2.sdk.NameIdentifierHelper
import org.mule.weave.v2.sdk.WeaveResource
import org.mule.weave.v2.sdk.WeaveResourceResolver

import scala.io.Source

class ProjectVirtualFileSystem(server: WeaveLanguageServer) extends VirtualFileSystem {

  def sourceRoot: Option[File] = {
    val params = server.params
    Option(params.getRootUri)
      .orElse(Option(params.getRootUri))
      .map((url) => {
        new File(new URL(url).getFile)
      })
  }

  override def removeChangeListener(listener: ChangeListener): Unit = {}

  override def file(path: String): VirtualFile = {
    val theFile = new File(path)
    if (theFile.exists()) {
      new FileBasedVirtualFile(this, theFile)
    } else if (sourceRoot.isDefined) {
      val theFile = new File(sourceRoot.get, path)
      if (theFile.exists()) {
        new FileBasedVirtualFile(this, theFile)
      } else {
        null
      }
    } else {
      null
    }
  }

  override def asResourceResolver: WeaveResourceResolver = {
    sourceRoot match {
      case Some(rootFile) => new RootDirectoryResourceResolver(rootFile)
      case None => EmptyWeaveResourceResolver
    }
  }

  override def changeListener(cl: ChangeListener): Unit = {}

  override def onChanged(vf: VirtualFile): Unit = {}
}

class RootDirectoryResourceResolver(root: File) extends WeaveResourceResolver {
  override def resolve(name: NameIdentifier): Option[WeaveResource] = {
    val path = NameIdentifierHelper.toWeaveFilePath(name)
    val theFile = new File(root, path)
    if (theFile.exists()) {
      val source = Source.fromFile(theFile, "UTF-8")
      try {
        Some(WeaveResource(theFile.toURI.toURL.toString, source.mkString))
      } finally {
        source.close()
      }
    } else {
      None
    }
  }
}
