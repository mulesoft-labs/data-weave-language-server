package org.mule.weave.lsp.vfs

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import scala.util.Try

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
}
