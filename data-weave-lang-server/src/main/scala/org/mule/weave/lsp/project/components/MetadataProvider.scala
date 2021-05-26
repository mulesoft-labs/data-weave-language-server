package org.mule.weave.lsp.project.components

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.project.service.WeaveAgentService
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils.isChildOf
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.ts.WeaveType

import java.io.File
import scala.collection.mutable

/**
  * Handles the metadata for a given script
  */
trait MetadataProvider {

  /**
    * All the input metadata defined for a given file
    *
    * @param vf The Virtual File
    * @return The Input Metadata for the specified file
    */
  def inputMetadataFor(vf: VirtualFile): InputMetadata

  /**
    * The output metadata defined for a given file
    *
    * @param vf The file
    * @return The output metadata if defined
    */
  def outputMetadataFor(vf: VirtualFile): Option[WeaveType]

}


case class InputMetadata(metadata: Array[WeaveTypeBind])

case class WeaveTypeBind(name: String, wtype: WeaveType)


class SampleBaseMetadataProvider(sampleDataManager: SampleDataManager, weaveAgentService: WeaveAgentService, eventBus: EventBus) extends MetadataProvider {

  val cache: mutable.HashMap[String, InputMetadata] = mutable.HashMap()

  eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {
    override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
      val keySet = cache.keySet
      keySet.foreach((scenario) => {
        if(isChildOf(uri, new File(scenario))){
          cache.remove(scenario)
        }
      })
    }
  })

  override def inputMetadataFor(vf: VirtualFile): InputMetadata = {
    val scenarios = sampleDataManager.listScenarios(vf.getNameIdentifier)
    val headOption = scenarios.headOption
    if (headOption.isDefined) {
      val scenario = headOption.get
      val scenarioPath = scenario.file.getAbsolutePath
      if (cache.contains(scenarioPath)) {
        cache(scenarioPath)
      } else {
        val value = weaveAgentService.inferInputMetadataForScenario(scenario)
        val metadata = value.get()
        cache.put(scenarioPath, metadata)
        metadata
      }
    } else {
      InputMetadata(Array.empty)
    }
  }

  override def outputMetadataFor(vf: VirtualFile): Option[WeaveType] = {
    None
  }
}

