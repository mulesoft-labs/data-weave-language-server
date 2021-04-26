package org.mule.weave.lsp.utils

object OSUtils {

  def isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("win")

}
