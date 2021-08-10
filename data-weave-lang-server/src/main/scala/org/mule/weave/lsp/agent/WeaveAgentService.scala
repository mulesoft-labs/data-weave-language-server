package org.mule.weave.lsp.agent

import org.eclipse.lsp4j
import org.eclipse.lsp4j.Position
import org.mule.weave.lsp.extension.client.EditorDecoration
import org.mule.weave.lsp.extension.client.EditorDecorationsParams
import org.mule.weave.lsp.extension.client.PreviewResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
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
import org.mule.weave.lsp.services.WeaveScenarioManagerService
import org.mule.weave.lsp.utils.EventBus
import org.mule.weave.lsp.utils.NetUtils
import org.mule.weave.v2.completion.DataFormatDescriptor
import org.mule.weave.v2.completion.DataFormatProperty
import org.mule.weave.v2.debugger.DebuggerPosition
import org.mule.weave.v2.debugger.DebuggerValue
import org.mule.weave.v2.debugger.client.ConnectionRetriesListener
import org.mule.weave.v2.debugger.client.DefaultWeaveAgentClientListener
import org.mule.weave.v2.debugger.client.WeaveAgentClient
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.debugger.event.ContextException
import org.mule.weave.v2.debugger.event.ContextResult
import org.mule.weave.v2.debugger.event.ContextValue
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
class WeaveAgentService(validationService: DataWeaveToolingService,
                        executor: Executor,
                        clientLogger: ClientLogger,
                        project: Project,
                        scenarioManagerService: WeaveScenarioManagerService,
                        client: WeaveLanguageClient
                       ) extends ToolingService {

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

  def inferInputMetadataForScenario(scenario: Scenario): CompletableFuture[Option[InputMetadata]] = {
    CompletableFuture.supplyAsync(() => {
      if (checkConnected()) {
        val result = new FutureValue[InputMetadata]()
        weaveAgentClient.inferInputsWeaveType(scenario.inputsDirectory().getAbsolutePath, new DefaultWeaveAgentClientListener {
          override def onImplicitWeaveTypesCalculated(event: ImplicitInputTypesEvent): Unit = {
            val binds: Array[WeaveTypeBind] = event.types.flatMap((m) => {
              validationService.loadType(m.wtypeString).map((wt) => WeaveTypeBind(m.name, wt))
            })
            result.set(InputMetadata(binds))
          }
        })
        result.get()
      } else {
        None
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
    val maybeScenario: Option[Scenario] = scenarioManagerService.activeScenario(nameIdentifier)
    run(nameIdentifier, content, url, maybeScenario)
  }

  def run(nameIdentifier: NameIdentifier, content: String, url: String, previewScenario: Option[Scenario]): PreviewResult = {
    if (checkConnected()) {
      val runResult = new FutureValue[PreviewResult]()
      val libs: Array[String] = projectKind.dependencyManager().dependencies().map(_.artifact.getAbsolutePath) //
      val sources: Array[String] = mainSourceFolders(projectKind.structure()).map(_.getAbsolutePath)
      val targets: Array[String] = mainTargetFolders(projectKind.structure()).map(_.getAbsolutePath)
      val inputsPath: String =
        previewScenario.map(_.inputsDirectory().getAbsolutePath).getOrElse("")
      val startTime = System.currentTimeMillis()
      val timeout = project.settings.previewTimeout.value().toLong
      val previewDebugEnabled = project.settings.enablePreviewDebug.value().toBoolean
      weaveAgentClient.runPreview(inputsPath, content, nameIdentifier.toString(), url, timeout, libs ++ sources ++ targets,
        new DefaultWeaveAgentClientListener {
          override def onPreviewExecuted(result: PreviewExecutedEvent): Unit = {
            val endTime = System.currentTimeMillis()
            result match {
              case PreviewExecutedFailedEvent(message, messages, contextData) => {
                if (contextData != null && contextData.nonEmpty) {
                  publishContextData(contextData, url, nameIdentifier)
                }
                val logsArray: Array[String] = messages.map((m) => m.timestamp + " : " + m.message).toArray
                runResult.set(
                  PreviewResult(
                    errorMessage = message,
                    success = false,
                    logs = util.Arrays.asList(logsArray: _*),
                    uri = url,
                    timeTaken = endTime - startTime
                  )
                )
              }
              case PreviewExecutedSuccessfulEvent(result, mimeType, extension, encoding, messages, contextData) => {
                val logsArray = messages.map((m) => m.timestamp + " : " + m.message).toArray
                if(contextData != null) {
                  publishContextData(contextData, url, nameIdentifier)
                }
                runResult.set(
                  PreviewResult(content = new String(result, encoding),
                    mimeType = mimeType,
                    success = true,
                    logs = util.Arrays.asList(logsArray: _*),
                    uri = url,
                    timeTaken = endTime - startTime
                  )
                )
              }
            }
          }
        }, previewDebugEnabled)

      runResult.get()
        .getOrElse({
          PreviewResult(errorMessage = "Unable to Start DataWeave Agent to Run Preview.", success = false, logs = util.Collections.emptyList(), uri = url)
        })
    } else {
      PreviewResult(errorMessage = "Unable to Start DataWeave Agent to Run Preview.", success = false, logs = util.Collections.emptyList(), uri = url)
    }
  }

  private def publishContextData(contextData: Array[ContextValue], url: String, nameIdentifier: NameIdentifier) = {
    client.clearEditorDecorations()
    val currentFileAnnotations: Array[ContextValue] = contextData.filter(_.location.start.resourceName == nameIdentifier.toString())
    val resultsByContextAndLine: Map[Int, Map[Int, ContextValue]] = currentFileAnnotations
      .groupBy(_.node.frameId)
      .mapValues((values) => {
        values.groupBy(_.location.end.line).mapValues(_.last)
      })

    val leftMargin: Int = {
      if (resultsByContextAndLine.isEmpty)
        0
      else
        currentFileAnnotations
          .maxBy((first) => {
            first.location.end.column
          }).location.end.column
    }


    val decorations: Array[EditorDecoration] = resultsByContextAndLine.flatMap((frameIdValues) => {
      frameIdValues._2.map((value) => {
        val cd = value._2
        val message = cd match {
          case ContextException(_, _, message) => {
            s" // Exception: " + message
          }
          case ContextResult(_, _, value, name) => {
            name match {
              case Some(name) => s" //${name} = ${trimToMaxLength(value)}"
              case None => s" // " + trimToMaxLength(value)
            }
          }
          case _ => ""
        }
        EditorDecoration(new lsp4j.Range(new Position(cd.location.end.line - 1, leftMargin), new Position(cd.location.end.line - 1, leftMargin)), message)
      })
    }).toArray

    client.setEditorDecorations(EditorDecorationsParams(url, util.Arrays.asList(decorations: _*)))
  }

  private def trimToMaxLength(value: DebuggerValue) = {
    val result = value.toString
    if (result.length > 100) {
      result.substring(0, 97) + "..."
    } else {
      result
    }
  }

  def toPosition(debuggerPosition: DebuggerPosition): Position = {
    val position = new lsp4j.Position()
    val column = if (debuggerPosition.column < 0) 0 else debuggerPosition.column - 1
    val line = if (debuggerPosition.line < 0) 0 else debuggerPosition.line - 1
    position.setCharacter(column)
    position.setLine(line)
    position
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
