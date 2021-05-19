package org.mule.weave.lsp.utils

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import scala.util.Try

/**
  * Helper functions that allows to handle URL strings
  */
object URLUtils {

  def toURI(path: String): Option[URI] = {
    Try(new URI(path)).toOption
  }

  def toFile(path: String): Option[File] = {
    toURI(path)
      .flatMap((uri) => Try(Paths.get(uri).toFile).toOption)
  }

  def toPath(path: String): Option[Path] = {
    toURI(path)
      .flatMap((uri) => {
        Try(Paths.get(uri)).toOption
      })
  }

  def isChildOf(child: String, parent: File): Boolean = {
    toPath(child).exists((path) => {
      path.toAbsolutePath.startsWith(parent.toPath)
    })
  }

  def isChildOfAny(child: String, parents: Array[File]): Boolean = {
    parents.exists((parent) => isChildOf(child, parent))
  }
}
