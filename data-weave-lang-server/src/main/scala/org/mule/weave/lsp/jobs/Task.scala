package org.mule.weave.lsp.jobs

trait Task {
  def run(cancelable: Status): Unit
}

case class Status(var canceled: Boolean = false)
