package org.mule.weave.lsp.commands

import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind

import scala.io.Source

class CreateMappingFileCommand(val project: ProjectKind, val weaveLanguageClient: WeaveLanguageClient) extends AbstractCreateFileCommand {

  val MAPPING_TEMPLATE: String = {
    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("dw-template-mapping.dwl"), "UTF-8")
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  override def commandId(): String = Commands.DW_CREATE_MAPPING


  def getTemplate = {
    MAPPING_TEMPLATE
  }

  def getDefaultName = {
    "MyMapping.dwl"
  }

  def getInputLabel = {
    "Name Of The Mapping"
  }
}
