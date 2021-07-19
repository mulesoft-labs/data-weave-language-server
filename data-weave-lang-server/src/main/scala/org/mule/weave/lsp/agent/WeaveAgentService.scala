package org.mule.weave.lsp.agent

import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.DependencyArtifact
import org.mule.weave.lsp.project.components.InputMetadata
import org.mule.weave.lsp.project.components.JavaWeaveLauncher
import org.mule.weave.lsp.project.components.ProjectStructure.mainSourceFolders
import org.mule.weave.lsp.project.components.ProjectStructure.mainTargetFolders
import org.mule.weave.lsp.project.components.Scenario
import org.mule.weave.lsp.project.components.WeaveTypeBind
import org.mule.weave.lsp.project.events.DependencyArtifactResolvedEvent
import org.mule.weave.lsp.project.events.OnDependencyArtifactResolved
import org.mule.weave.lsp.project.events.OnProjectStarted
import org.mule.weave.lsp.project.events.ProjectStartedEvent
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.services.DataWeaveToolingService
import org.mule.weave.lsp.services.ToolingService
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.NetUtils
import org.mule.weave.v2.completion.DataFormatDescriptor
import org.mule.weave.v2.completion.DataFormatProperty
import org.mule.weave.v2.debugger.client.ConnectionRetriesListener
import org.mule.weave.v2.debugger.client.DefaultWeaveAgentClientListener
import org.mule.weave.v2.debugger.client.WeaveAgentClient
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.debugger.event.DataFormatsDefinitionsEvent
import org.mule.weave.v2.debugger.event.ImplicitInputTypesEvent
import org.mule.weave.v2.debugger.event.InferWeaveTypeEvent
import org.mule.weave.v2.debugger.event.PreviewExecutedEvent
import org.mule.weave.v2.debugger.event.PreviewExecutedFailedEvent
import org.mule.weave.v2.debugger.event.PreviewExecutedSuccessfulEvent
import org.mule.weave.v2.debugger.event.WeaveDataFormatProperty
import org.mule.weave.v2.parser.ast.variables.NameIdentifier
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import scala.collection.JavaConverters.asScalaBufferConverter

/**
  * This service manages the WeaveAgent. This agent allows to query and execute scripts on a running DataWeave Engine.
  *
  */
class WeaveAgentService(validationService: DataWeaveToolingService, executor: Executor, clientLogger: ClientLogger, project: Project) extends ToolingService {

  private var agentProcess: Process = _
  private var weaveAgentClient: WeaveAgentClient = _
  private var projectKind: ProjectKind = _

  private val startAgentLock: Lock = new ReentrantLock()

  override def init(projectKind: ProjectKind, eventBus: EventBus): Unit = {
    this.projectKind = projectKind
    eventBus.register(DependencyArtifactResolvedEvent.ARTIFACT_RESOLVED, new OnDependencyArtifactResolved {
      override def onArtifactsResolved(artifacts: Array[DependencyArtifact]): Unit = {
        restart()
      }
    })

    eventBus.register(ProjectStartedEvent.PROJECT_STARTED, new OnProjectStarted {
      override def onProjectStarted(project: Project): Unit = {
        startAgent()
      }
    })
  }

  private def restart(): Unit = {
    stopAgent()
    startAgent()
  }

  private val ERROR_STREAM = "Error"

  def startAgent(): Unit = {
    if (startAgentLock.tryLock()) {
      try {
        if (!isProcessAlive) {
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
          clientLogger.logInfo(s"[weave-agent] Starting Agent: ${args.asScala.mkString(" ")}")
          forwardStream(agentProcess.getInputStream, "Info")
          forwardStream(agentProcess.getErrorStream, ERROR_STREAM)
          val clientProtocol = new TcpClientProtocol("localhost", port)
          weaveAgentClient = new WeaveAgentClient(clientProtocol, new DefaultWeaveAgentClientListener())
          weaveAgentClient.connect(50, 500, new ConnectionRetriesListener {
            override def startConnecting(): Unit = {}

            override def connectedSuccessfully(): Unit = {
              clientLogger.logInfo(s"[weave-agent] Weave Agent connected at: ${port}")
            }

            override def failToConnect(reason: String): Unit = {
              clientLogger.logError(s"[weave-agent] Fail to connect to agent: ${reason}")
            }

            override def onRetry(count: Int, total: Int): Boolean = {
              if (!isProcessAlive) {
                clientLogger.logError(s"[weave-agent] Will not retry as process is no longer alive and exit code was: `${agentExitCode}`.")
                false
              } else {
                clientLogger.logError(s"[weave-agent] Retrying to connect: ${count}/${total}.")
                true
              }
            }
          })
          clientLogger.logInfo(s"[weave-agent] Weave Agent Started at port :${port}.")
          if (!weaveAgentClient.isConnected()) {
            clientLogger.logError(s"[weave-agent] Unable to connect to Weave Agent")
          }
        }
      } finally {
        startAgentLock.unlock()
      }
    }
  }

  private def agentExitCode = {
    if (agentProcess == null) -1 else agentProcess.exitValue()
  }

  private def isProcessAlive = {
    agentProcess != null && agentProcess.isAlive
  }

  override def start(): Unit = {}

  private def forwardStream(is: InputStream, kind: String): Unit = {
    executor.execute(() => {
      try {
        val theProcess: Process = agentProcess
        val reader: BufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
        while (theProcess.isAlive) {
          val line = reader.readLine()
          if (line != null) {
            if (kind == ERROR_STREAM) {
              clientLogger.logError(s"[weave-agent] ${line}")
            } else {
              clientLogger.logInfo(s"[weave-agent] ${line}")
            }
          }
        }
      } catch {
        case io: IOException => {
          clientLogger.logError("[weave-agent] Error on Agent", io)
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
        result.get().getOrElse(InputMetadata(Array.empty))
      } else {
        InputMetadata(Array.empty)
      }
    }, executor)
  }

  def checkConnected(): Boolean = {
    if (isDisconnected) {
      clientLogger.logInfo("[weave-agent] Restarting Agent as is not initialized.")
      restart()
    }
    weaveAgentClient != null && weaveAgentClient.isConnected()
  }

  private def isDisconnected = {
    weaveAgentClient == null || !weaveAgentClient.isConnected() || !isProcessAlive
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
          result.get().flatten
        } else {
          None
        }
      } else {
        None
      }
    }, executor)
  }

  def run(nameIdentifier: NameIdentifier, content: String, url: String): PreviewResult = {
    if (checkConnected()) {
      val runResult = new FutureValue[PreviewResult]()
      val libs: Array[String] = projectKind.dependencyManager().dependencies().map(_.artifact.getAbsolutePath) //
      val sources: Array[String] = mainSourceFolders(projectKind.structure()).map(_.getAbsolutePath)
      val targets: Array[String] = mainTargetFolders(projectKind.structure()).map(_.getAbsolutePath)

      val inputsPath: String =
        projectKind.sampleDataManager().flatMap((sampleManager) => {
          //TODO we should have a way to pick what scenario and store it somewhere. Maybe Configuration Objects
          sampleManager.listScenarios(nameIdentifier).headOption
            .map(_.inputs().getAbsolutePath)
        }).getOrElse("")
      val startTime = System.currentTimeMillis()
      weaveAgentClient.runPreview(inputsPath, content, nameIdentifier.toString(), url, project.settings.previewTimeout.value().toLong, libs ++ sources ++ targets,
        new DefaultWeaveAgentClientListener {
          override def onPreviewExecuted(result: PreviewExecutedEvent): Unit = {
            val endTime = System.currentTimeMillis()
            result match {
              case PreviewExecutedFailedEvent(message, messages) => {
                val logsArray: Array[String] = messages.map((m) => m.timestamp + " : " + m.message).toArray
                runResult.set(PreviewResult(errorMessage = message, success = false, logs = util.Arrays.asList(logsArray: _*), uri = url, timeTaken = endTime - startTime))
              }
              case PreviewExecutedSuccessfulEvent(result, mimeType, extension, encoding, messages) => {
                val logsArray = messages.map((m) => m.timestamp + " : " + m.message).toArray
                runResult.set(PreviewResult(content = new String(result, encoding), mimeType = mimeType, success = true, logs = util.Arrays.asList(logsArray: _*), uri = url, timeTaken = endTime - startTime))
              }
            }
          }
        })
      runResult.get()
        .getOrElse({
          PreviewResult(errorMessage = "Unable to Start DataWeave Agent to Run Preview.", success = false, logs = util.Collections.emptyList(), uri = url)
        })
    } else {
      PreviewResult(errorMessage = "Unable to Start DataWeave Agent to Run Preview.", success = false, logs = util.Collections.emptyList(), uri = url)
    }

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
        result.get().getOrElse(Array.empty)
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
    stopAgent()
  }

  private def stopAgent(): Unit = {
    if (weaveAgentClient != null) {
      weaveAgentClient.disconnect()
    }
    if (agentProcess != null) {
      agentProcess.destroyForcibly()
      agentProcess = null
    }
  }
}

class FutureValue[T] {
  @volatile
  var value: T = _
  val countDownLatch = new CountDownLatch(1)

  def get(): Option[T] = {
    if (value == null) {
      countDownLatch.await(30, TimeUnit.SECONDS)
    }
    Option(value)
  }

  def set(v: T): Unit = {
    value = v
    countDownLatch.countDown()
  }
}
