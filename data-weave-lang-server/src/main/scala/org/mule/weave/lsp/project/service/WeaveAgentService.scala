package org.mule.weave.lsp.project.service

import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.InputMetadata
import org.mule.weave.lsp.project.components.JavaWeaveLauncher
import org.mule.weave.lsp.project.components.Scenario
import org.mule.weave.lsp.project.components.WeaveTypeBind
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.project.events.OnDependencyArtifactResolved
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.NetUtils
import org.mule.weave.v2.completion.DataFormatDescriptor
import org.mule.weave.v2.completion.DataFormatProperty
import org.mule.weave.v2.debugger.client.DefaultWeaveAgentClientListener
import org.mule.weave.v2.debugger.client.WeaveAgentClient
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.debugger.event.DataFormatsDefinitionsEvent
import org.mule.weave.v2.debugger.event.ImplicitInputTypesEvent
import org.mule.weave.v2.debugger.event.InferWeaveTypeEvent
import org.mule.weave.v2.debugger.event.WeaveDataFormatProperty
import org.mule.weave.v2.ts.WeaveType

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
  * This service manages the WeaveAgent. This agent allows to query and execute scripts on a running DataWeave Engine.
  *
  */
class WeaveAgentService(eventBus: EventBus, validationService: DataWeaveToolingService, executor: Executor, clientLogger: ClientLogger) extends ToolingService {

  private var agentProcess: Process = _
  private var weaveAgentClient: WeaveAgentClient = _
  private var projectKind: ProjectKind = _

  val lock: Lock = new ReentrantLock()

  override def init(projectKind: ProjectKind): Unit = {
    this.projectKind = projectKind
  }

  eventBus.register(DependencyArtifactResolvedEvent.ARTIFACT_RESOLVED, new OnDependencyArtifactResolved {
    override def onArtifactsResolved(artifacts: Array[DependencyArtifact]): Unit = {
      restart()
    }
  })

  private def restart(): Unit = {
    stop()
    start()
  }

  override def start(): Unit = {
    if (lock.tryLock()) {
      try {
        if (agentProcess == null) {
          val port: Int = NetUtils.freePort()
          val commandArgs: util.ArrayList[String] = JavaWeaveLauncher.buildJavaProcessBaseArgs(projectKind)
          val builder = new ProcessBuilder()
          val args = new util.ArrayList[String]()
          args.addAll(commandArgs)
          args.add("--agent")
          args.add("-p")
          args.add(port.toString)
          builder.command(args)
          agentProcess = builder.start()
          forwardStream(agentProcess.getInputStream, "Info")
          forwardStream(agentProcess.getErrorStream, "Error")
          val clientProtocol = new TcpClientProtocol("localhost", port)
          weaveAgentClient = new WeaveAgentClient(clientProtocol, new DefaultWeaveAgentClientListener())
          weaveAgentClient.connect()
          clientLogger.logInfo(s"Weave Agent Started at port :${port}.")
          if (!weaveAgentClient.isConnected()) {
            clientLogger.logError(s"Unable to connect to Weave Agent")
          }
        }
      } finally {
        lock.unlock()
      }
    }
  }

  private def forwardStream(is: InputStream, kind: String): Unit = {
    executor.execute(() => {
      try {
        val theProcess: Process = agentProcess
        val reader: BufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
        while (theProcess.isAlive) {
          val line = reader.readLine()
          if (line != null) {
            clientLogger.logInfo(s"[debugger-agent][${kind}] ${line}")
          }
        }
      } catch {
        case io: IOException => {
          clientLogger.logInfo("Error on Agent", io)
        }
      }
    })
  }

  def inferInputMetadataForScenario(scenario: Scenario): CompletableFuture[InputMetadata] = {
    CompletableFuture.supplyAsync(() => {
      if (checkConnected()) {
        val result = new FutureValue[InputMetadata]()
        weaveAgentClient.inferInputsWeaveType(scenario.inputs().getAbsolutePath, new DefaultWeaveAgentClientListener {
          override def onImplicitWeaveTypesCalculated(event: ImplicitInputTypesEvent): Unit = {
            val binds: Array[WeaveTypeBind] = event.types.flatMap((m) => {
              validationService.loadType(m.wtypeString).map((wt) => WeaveTypeBind(m.name, wt))
            })
            result.set(InputMetadata(binds))
          }
        })
        result.get()
      } else {
        InputMetadata(Array.empty)
      }
    }, executor)
  }

  def checkConnected(): Boolean = {
    if (weaveAgentClient == null || !weaveAgentClient.isConnected()) {
      restart()
    }
    weaveAgentClient != null && weaveAgentClient.isConnected()
  }

  def inferOutputMetadataForScenario(scenario: Scenario): CompletableFuture[Option[WeaveType]] = {
    CompletableFuture.supplyAsync(() => {
      if (checkConnected()) {
        val result = new FutureValue[Option[WeaveType]]()
        val maybeExpected = scenario.expected()
        if (maybeExpected.isDefined) {
          weaveAgentClient.inferWeaveType(maybeExpected.get.getAbsolutePath, new DefaultWeaveAgentClientListener {
            override def onWeaveTypeInfer(event: InferWeaveTypeEvent): Unit = {
              result.set(validationService.loadType(event.typeString))
            }
          })
          result.get()
        } else {
          None
        }
      } else {
        None
      }
    }, executor)
  }


  def definedDataFormats(): CompletableFuture[Array[DataFormatDescriptor]] = {
    CompletableFuture.supplyAsync(() => {
      if (checkConnected()) {
        val result = new FutureValue[Array[DataFormatDescriptor]]()
        weaveAgentClient.definedDataFormats(new DefaultWeaveAgentClientListener {
          override def onDataFormatDefinitionCalculated(event: DataFormatsDefinitionsEvent): Unit = {
            val formats = event.formats
            val descriptor = formats.map((weaveDataFormatDescriptor) => {
              val mimeType = weaveDataFormatDescriptor.mimeType
              DataFormatDescriptor(mimeType, weaveDataFormatDescriptor.id, toDataFormatProp(weaveDataFormatDescriptor.writerProperties), toDataFormatProp(weaveDataFormatDescriptor.readerProperties))
            })
            result.set(descriptor)
          }
        })
        result.get()
      } else {
        Array.empty
      }
    }, executor)
  }

  private def toDataFormatProp(weaveDataFormatPropertySeq: Array[WeaveDataFormatProperty]): Array[DataFormatProperty] = {
    weaveDataFormatPropertySeq.map((property) => {
      DataFormatProperty(property.name, property.description, property.wtype, property.values)
    })
  }

  override def stop(): Unit = {
    if (weaveAgentClient != null) {
      weaveAgentClient.disconnect()
    }
    if (agentProcess != null) {
      agentProcess.destroyForcibly()
    }
  }
}

class FutureValue[T] {
  @volatile
  var value: T = _
  val countDownLatch = new CountDownLatch(1)

  def get(): T = {
    if (value == null) {
      countDownLatch.await()
    }
    value
  }

  def set(v: T): Unit = {
    value = v
    countDownLatch.countDown()
  }
}
