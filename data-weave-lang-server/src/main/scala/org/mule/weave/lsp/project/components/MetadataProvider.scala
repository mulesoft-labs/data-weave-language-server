package org.mule.weave.lsp.project.components

import org.eclipse.lsp4j.FileChangeType
import org.mule.weave.lsp.agent.WeaveAgentService
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.services.events.FileChangedEvent
import org.mule.weave.lsp.services.events.OnFileChanged
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.URLUtils.isChildOf
import org.mule.weave.v2.editor.VirtualFile
import org.mule.weave.v2.ts.WeaveType

import java.io.File
import java.util.concurrent.CompletableFuture
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


class SampleBaseMetadataProvider(weaveAgentService: WeaveAgentService, eventBus: EventBus, weaveScenarioManagerService: WeaveScenarioManagerService) extends MetadataProvider {

  private val inputMetadataCache: mutable.HashMap[String, InputMetadata] = mutable.HashMap()
  private val outputMetadataCache: mutable.HashMap[String, WeaveType] = mutable.HashMap()

  eventBus.register(FileChangedEvent.FILE_CHANGED_EVENT, new OnFileChanged {
    override def onFileChanged(uri: String, changeType: FileChangeType): Unit = {
      val keySet = inputMetadataCache.keySet
      keySet.foreach((scenario) => {
        if (isChildOf(uri, new File(scenario))) {
          inputMetadataCache.remove(scenario)
          outputMetadataCache.remove(scenario)
        }
      })
    }
  })

  override def inputMetadataFor(vf: VirtualFile): InputMetadata = {
    val headOption = weaveScenarioManagerService.activeScenario(vf.getNameIdentifier)
    if (headOption.isDefined) {
      val scenario = headOption.get
      val scenarioPath = scenario.file.getAbsolutePath
      if (inputMetadataCache.contains(scenarioPath)) {
        inputMetadataCache(scenarioPath)
      } else {
        val value: CompletableFuture[Option[InputMetadata]] = weaveAgentService.inferInputMetadataForScenario(scenario)
        val metadata: Option[InputMetadata] = value.get()
        if (metadata.isDefined) {
          inputMetadataCache.put(scenarioPath, metadata.get)
          metadata.get
        } else {
          InputMetadata(Array.empty)
        }
      }
    } else {
      InputMetadata(Array.empty)
    }
  }

  override def outputMetadataFor(vf: VirtualFile): Option[WeaveType] = {
    val headOption = weaveScenarioManagerService.activeScenario(vf.getNameIdentifier)
    if (headOption.isDefined) {
      val scenario = headOption.get
      val scenarioPath = scenario.file.getAbsolutePath
      if (outputMetadataCache.contains(scenarioPath)) {
        outputMetadataCache.get(scenarioPath)
      } else {
        if (scenario.expected().isDefined) {
          val outputMetadataFuture = weaveAgentService.inferOutputMetadataForScenario(scenario)
          val expectedMetadata: Option[WeaveType] = outputMetadataFuture.get()
          if (expectedMetadata.isDefined) {
            outputMetadataCache.put(scenarioPath, expectedMetadata.get)
          }
          expectedMetadata
        } else {
          None
        }
      }
    } else {
      None
    }
  }
}

