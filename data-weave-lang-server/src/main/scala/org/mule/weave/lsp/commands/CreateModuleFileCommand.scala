package org.mule.weave.lsp.commands

import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind

import scala.io.Source

class CreateModuleFileCommand(val project: ProjectKind, val weaveLanguageClient: WeaveLanguageClient) extends AbstractCreateFileCommand {

  val MODULE_TEMPLATE: String = {
    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("dw-template-module.dwl"), "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  override def commandId(): String = Commands.DW_CREATE_MODULE

  def getTemplate = {
    MODULE_TEMPLATE
  }

  def getDefaultName = {
    "MyModule.dwl"
  }

  def getInputLabel = {
    "Name Of The Module"
  }
}
