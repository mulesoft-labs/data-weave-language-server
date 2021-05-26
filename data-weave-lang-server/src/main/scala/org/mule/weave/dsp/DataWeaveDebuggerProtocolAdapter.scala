package org.mule.weave.dsp

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments
import org.eclipse.lsp4j.debug.RunInTerminalResponse
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.ProcessLauncher
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.v2.debugger.ArrayDebuggerValue
import org.mule.weave.v2.debugger.AttributeDebuggerValue
import org.mule.weave.v2.debugger.DebuggerFrame
import org.mule.weave.v2.debugger.DebuggerFunction
import org.mule.weave.v2.debugger.DebuggerValue
import org.mule.weave.v2.debugger.FieldDebuggerValue
import org.mule.weave.v2.debugger.KeyDebuggerValue
import org.mule.weave.v2.debugger.ObjectDebuggerValue
import org.mule.weave.v2.debugger.SimpleDebuggerValue
import org.mule.weave.v2.debugger.WeaveBreakpoint
import org.mule.weave.v2.debugger.WeaveExceptionBreakpoint
import org.mule.weave.v2.debugger.client.DebuggerClient
import org.mule.weave.v2.debugger.client.DebuggerClientListener
import org.mule.weave.v2.debugger.client.ScriptEvaluationListener
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.debugger.event.ClientInitializedEvent
import org.mule.weave.v2.debugger.event.OnFrameEvent
import org.mule.weave.v2.debugger.event.ScriptResultEvent
import org.mule.weave.v2.debugger.event.UnexpectedServerErrorEvent
import org.mule.weave.v2.editor.VirtualFileSystem
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.String.valueOf
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class DataWeaveDebuggerProtocolAdapter(virtualFileSystem: VirtualFileSystem,
                                       clientLogger: ClientLogger,
                                       languageClient: WeaveLanguageClient,
                                       launcher: ProcessLauncher,
                                       projectKind: ProjectKind,
                                       executor: ExecutorService
                                      ) extends IDebugProtocolServer with DebuggerClientListener {

  val MAX_RETRY = 20

  private val logger: Logger = Logger.getLogger(getClass.getName)

  private val ROOT_REF = 2147483640

  private var debuggerClient: DebuggerClient = _
  private var protocolClient: IDebugProtocolClient = _

  private var debuggerStatus: OnFrameEvent = _
  private var selectedFrame: DebuggerFrame = _

  private val variablesRegistry = ArrayBuffer[DebuggerValue]()

  private var process: Option[Process] = None

  def connect(client: IDebugProtocolClient): Unit = {
    protocolClient = client
  }


  override def initialize(args: InitializeRequestArguments): CompletableFuture[Capabilities] = {
    logger.log(Level.INFO, "[DataWeaveDebuggerProtocolAdapter] initialize: " + args)
    val capabilities = new Capabilities()
    capabilities.setSupportsConditionalBreakpoints(true)
    capabilities.setSupportsStepInTargetsRequest(true)
    capabilities.setSupportsGotoTargetsRequest(false)
    capabilities.setSupportsConfigurationDoneRequest(true)
    CompletableFuture.completedFuture(capabilities)
  }

  override def configurationDone(args: ConfigurationDoneArguments): CompletableFuture[Void] = {
    clientLogger.logInfo("Configuration Done")
    CompletableFuture.supplyAsync(() => {
      var connected = false
      var i = 0
      while (!connected && i < MAX_RETRY) {
        try {
          debuggerClient.connect()
            .onResponse((ci) => {
              clientLogger.logInfo("Weave Debugger Client Connected.")
              logger.log(Level.INFO, "Debugger Initialized")
            })
          connected = true
        } catch {
          case e: Exception => {
            if (i + 1 == MAX_RETRY) {
              logger.log(Level.SEVERE, "Unable to connect to client", e)
              val stackTrace = new StringWriter()
              e.printStackTrace(new PrintWriter(stackTrace))
              clientLogger.logError("Unable to connect to client: \n" + stackTrace.toString)
              val arguments = new ExitedEventArguments
              arguments.setExitCode(-1)
              protocolClient.exited(arguments)
              protocolClient.terminated(new TerminatedEventArguments())
            } else {
              //Wait for one sec
              java.lang.Thread.sleep(1000)
            }
          }
        }
        i = i + 1
      }
      null
    }, executor)
  }


  override def attach(args: util.Map[String, AnyRef]): CompletableFuture[Void] = {
    val configType: launcher.ConfigType = launcher.parseArgs(args.asScala.toMap)
    logger.log(Level.INFO, "[DataWeaveDebuggerProtocolAdapter] attach: " + args)
    CompletableFuture.supplyAsync(() => {
      connectDebugger(configType)
      null
    }, executor)
  }

  private def connectDebugger(configType: launcher.ConfigType): Unit = {
    debuggerClient = new DebuggerClient(this, TcpClientProtocol(port = configType.debuggerPort))
    protocolClient.initialized()
  }


  override def breakpointLocations(args: BreakpointLocationsArguments): CompletableFuture[BreakpointLocationsResponse] = super.breakpointLocations(args)

  override def launch(args: util.Map[String, AnyRef]): CompletableFuture[Void] = {
    logger.log(Level.INFO, "[DataWeaveDebuggerProtocolAdapter] launch: " + args)
    CompletableFuture.runAsync(() => {
      val config: launcher.ConfigType = launcher.parseArgs(args.asScala.toMap)
      val debugMode = valueOf(args.get("noDebug")) != "true"

      //Trigger build before each run
      if (config.buildBefore) {
        projectKind.buildManager().build()
      }

      process = launcher.launch(config, debugMode)
      if (process.isDefined) {
        executor.execute(() => {
          //Block the process
          val exitValue = process.get.waitFor()
          val arguments = new ExitedEventArguments()
          arguments.setExitCode(exitValue)
          clientLogger.logInfo(s"Process Finished with ${exitValue}")
          protocolClient.exited(arguments)
          protocolClient.terminated(new TerminatedEventArguments())
        })

        forwardStream(process.get.getInputStream, OutputEventArgumentsCategory.STDOUT)
        forwardStream(process.get.getErrorStream, OutputEventArgumentsCategory.STDERR)

        if (debugMode) {
          connectDebugger(config)
        }
      } else {
        languageClient.showMessage(new MessageParams(MessageType.Error, "Unable To Start Process."))
        val arguments = new ExitedEventArguments()
        arguments.setExitCode(-1)
        protocolClient.exited(arguments)
        protocolClient.terminated(new TerminatedEventArguments())
      }
    })
  }

  private def forwardStream(is: InputStream, kind: String): Unit = {
    executor.execute(() => {
      try {
        val theProcess: Process = process.get
        val reader: BufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
        while (theProcess.isAlive) {
          val line = reader.readLine()
          if (line != null && !line.startsWith("[dw-debugger]")) {
            val output: OutputEventArguments = new OutputEventArguments()
            output.setOutput(line + "\n")
            output.setCategory(kind)
            protocolClient.output(output)
          }
        }
      } catch {
        case io: IOException => {
          logger.log(Level.INFO, io.getMessage)
        }
      }
    })
  }

  override def disconnect(args: DisconnectArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "[DataWeaveDebuggerProtocolAdapter] disconnect: " + args)
    CompletableFuture.supplyAsync(() => {
      if (debuggerClient != null) {
        debuggerClient.disconnect()
      }
      if (process.nonEmpty) {
        process.get.destroyForcibly()
      }
      null
    }, executor)
  }

  override def terminate(args: TerminateArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] terminate: " + args)
    CompletableFuture.supplyAsync(() => {
      if (process.isDefined) {
        process.get.destroy()
      }
      null
    })
  }


  override def onClientInitialized(ci: ClientInitializedEvent): Unit = {
    logger.log(Level.INFO, "onClientInitialized")
  }

  override def onFrame(client: DebuggerClient, frame: OnFrameEvent): Unit = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] onFrame: " + frame)
    val arguments = new StoppedEventArguments()
    arguments.setAllThreadsStopped(true)
    arguments.setThreadId(1)
    arguments.setReason(StoppedEventArgumentsReason.PAUSE)
    protocolClient.stopped(arguments)
    debuggerStatus = frame
    variablesRegistry.clear()
  }


  override def runInTerminal(args: RunInTerminalRequestArguments): CompletableFuture[RunInTerminalResponse] = {
    super.runInTerminal(args)
  }

  override def onUnexpectedError(unexpectedServerErrorEvent: UnexpectedServerErrorEvent): Unit = {
    clientLogger.logError("Unexpected error while debugging: " + unexpectedServerErrorEvent.stacktrace)
  }

  override def setBreakpoints(args: SetBreakpointsArguments): CompletableFuture[SetBreakpointsResponse] = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] setBreakpoints: " + args)
    CompletableFuture.supplyAsync(() => {
      val sourceBreakpoints: Array[SourceBreakpoint] = args.getBreakpoints
      val dwBreakpoints = sourceBreakpoints.flatMap((bp) => {
        val path = new File(args.getSource.getPath)
        val virtualFile = virtualFileSystem.file(path.toURI.toURL.toString)
        if (virtualFile != null) {
          val nameIdentifier = virtualFile.getNameIdentifier.toString()
          logger.log(Level.INFO, "[DWDebuggerAdapter] Adding breakpoint to " + nameIdentifier + " at " + bp.getLine)
          Some(new WeaveBreakpoint(bp.getLine, nameIdentifier))
        } else {
          None
        }
      })
      debuggerClient.clearBreakpoints()
      debuggerClient.addBreakpoints(dwBreakpoints)
      val response = new SetBreakpointsResponse
      val responseBreakpoints = sourceBreakpoints.map(toBreakpoint(_, args.getSource))
      response.setBreakpoints(responseBreakpoints)
      response
    })
  }


  private def toBreakpoint(sourceBreakpoint: SourceBreakpoint, source: Source) = {
    val breakpoint = new Breakpoint
    breakpoint.setVerified(true)
    breakpoint.setLine(sourceBreakpoint.getLine)
    breakpoint.setColumn(sourceBreakpoint.getColumn)
    breakpoint.setSource(source)
    breakpoint
  }


  override def continue_(args: ContinueArguments): CompletableFuture[ContinueResponse] = {
    CompletableFuture.supplyAsync(() => {
      debuggerClient.resume()
      null
    })
  }

  override def next(args: NextArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "next" + args)
    CompletableFuture.supplyAsync(() => {
      debuggerClient.nextStep()
      null
    })
  }

  override def stepIn(args: StepInArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "stepIn" + args)
    CompletableFuture.supplyAsync(() => {
      debuggerClient.stepInto()
      null
    })
  }


  override def setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "setExceptionBreakpoints" + args)
    CompletableFuture.completedFuture({
      debuggerClient.addExceptionBreakpoints({
        args.getFilters.map((f) => {
          WeaveExceptionBreakpoint(f)
        })
      })
      null
    })
  }

  override def stackTrace(args: StackTraceArguments): CompletableFuture[StackTraceResponse] = {
    logger.log(Level.INFO, "stackTrace" + args)
    CompletableFuture.supplyAsync(() => {
      val response = new StackTraceResponse
      if (debuggerStatus != null) {
        val frames = debuggerStatus.frames
        val stackFrame = frames.map((f) => {
          val frame = new StackFrame()
          frame.setColumn(f.startPosition.column)
          frame.setLine(f.startPosition.line)
          if (f.endPosition != null) {
            frame.setEndColumn(f.endPosition.column)
            frame.setEndLine(f.endPosition.line)
          }
          frame.setName(f.name.getOrElse("Anonymous"))
          frame.setModuleId(f.startPosition.resourceName)
          frame.setSource(toSource(f.startPosition.resourceName))
          frame.setPresentationHint(StackFramePresentationHint.NORMAL)
          frame.setId(f.id)
          frame
        })

        //Set the current position to the current frame
        stackFrame.lastOption.foreach((current) => {
          current.setLine(debuggerStatus.startPosition.line)
          current.setColumn(debuggerStatus.startPosition.column)
          if (debuggerStatus.endPosition != null) {
            current.setEndColumn(debuggerStatus.endPosition.column)
            current.setEndLine(debuggerStatus.endPosition.line)
          }
        })

        response.setStackFrames(stackFrame.reverse)
      }
      logger.log(Level.INFO, "[DWDebuggerAdapter] stackTrace -> Response : " + response)
      response
    })
  }


  override def scopes(args: ScopesArguments): CompletableFuture[ScopesResponse] = {
    logger.log(Level.INFO, "Scopes" + args)
    CompletableFuture.supplyAsync(() => {
      val response = new ScopesResponse
      if (debuggerStatus != null) {
        val frameId = args.getFrameId
        val maybeFrame = debuggerStatus.frames.find((df) => df.id == frameId)
        selectedFrame = maybeFrame.orNull
        val scope = new Scope()
        scope.setName("Variables")
        scope.setVariablesReference(ROOT_REF)
        response.setScopes(Array(scope))
      }
      logger.log(Level.INFO, "[DWDebuggerAdapter] scopes -> Response : " + response)
      response
    })
  }

  private def addToVariableRegistry(variableValue: DebuggerValue): Int = {
    variablesRegistry.+=(variableValue)
    variablesRegistry.size
  }

  def toSource(nameIdentifier: String): Source = {
    val result = new Source()
    result.setName(nameIdentifier)
    val resource = virtualFileSystem.asResourceResolver.resolve(NameIdentifier(nameIdentifier))
    resource.foreach((wr) => {
      result.setPath(wr.url())
    })
    result
  }


  override def stepOut(args: StepOutArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] variables : " + args)
    CompletableFuture.supplyAsync(() => {
      debuggerClient.stepOut()
      null
    })
  }

  override def variables(args: VariablesArguments): CompletableFuture[VariablesResponse] = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] variables : " + args)
    CompletableFuture.supplyAsync(() => {
      val response = new VariablesResponse
      //Scopes are using negative ids
      if (debuggerStatus != null && selectedFrame != null) {
        val variables: Array[Variable] = {
          if (args.getVariablesReference == ROOT_REF) {
            selectedFrame.values.map((v) => {
              val debuggerVariable = v._2
              val variable = new Variable
              variable.setName(v._1)
              setVariableValue(debuggerVariable, variable)
              setChildNumbers(debuggerVariable, variable)
              variable.setType(debuggerVariable.typeName())
              variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.DATA))
              variable
            })
          }
          else if (registryContainsVariable(args)) {
            val value = variablesRegistry(args.getVariablesReference - 1)
            getChildVariablesOf(value)
          } else {
            logger.log(Level.INFO, s"[DWDebuggerAdapter] variable ${args.getVariablesReference} Is not present in registry  : \n-" + variablesRegistry.map(_.typeName()).mkString("\n- "))
            Array()
          }
        }
        response.setVariables(variables)
      }
      logger.log(Level.INFO, "[DWDebuggerAdapter] variables -> Response :" + response)
      response
    })
  }


  private def setVariableValue(debuggerVariable: DebuggerValue, variable: Variable): Unit = {
    variable.setValue(debuggerVariable.toString)
  }

  private def setChildNumbers(debuggerVariable: DebuggerValue, variable: Variable): Unit = {
    debuggerVariable match {
      case ObjectDebuggerValue(fields, _, _) => {
        variable.setNamedVariables(fields.length)
      }
      case ArrayDebuggerValue(values, _, _) => {
        variable.setIndexedVariables(values.length)
      }
      case _ =>
    }

    if (hasChildren(debuggerVariable)) {
      variable.setVariablesReference(addToVariableRegistry(debuggerVariable))
    }
  }

  def hasChildren(debuggerVariable: DebuggerValue): Boolean = {
    debuggerVariable match {
      case _: ObjectDebuggerValue => {
        true
      }
      case _: ArrayDebuggerValue => {
        true
      }
      case field: FieldDebuggerValue => {
        field.key.attr.nonEmpty || hasChildren(field.value)
      }
      case _ => {
        false
      }
    }
  }

  private def createVariableKind(property: String): VariablePresentationHint = {
    val hint = new VariablePresentationHint()
    hint.setKind(property)
    hint
  }

  private def registryContainsVariable(args: VariablesArguments): Boolean = {
    variablesRegistry.size >= args.getVariablesReference
  }

  private def getChildVariablesOf(value: DebuggerValue): Array[Variable] = {
    value match {
      case FieldDebuggerValue(key, value, location) => {
        getVariablesOf(key) ++ getVariablesOf(value)
      }
      case at: AttributeDebuggerValue => {
        getVariablesOf(at)
      }
      case of: ObjectDebuggerValue => {
        getVariablesOf(of)
      }
      case ad: ArrayDebuggerValue => {
        getVariablesOf(ad)
      }
      case _: SimpleDebuggerValue => {
        Array()
      }
      case kd: KeyDebuggerValue => {
        getVariablesOf(kd)
      }
      case df: DebuggerFunction => {
        getVariablesOf(df)
      }
    }
  }


  private def getVariablesOf(value: DebuggerValue): Array[Variable] = {
    value match {
      case fd@FieldDebuggerValue(key, value, _) => {
        val variable = new Variable()
        variable.setName(key.name)
        variable.setType(value.typeName())
        setVariableValue(value, variable)
        setChildNumbers(fd, variable)
        variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.DATA))
        Array(variable)
      }
      case AttributeDebuggerValue(name, value, _) => {
        val variable = new Variable()
        variable.setName("@" + name)
        variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.PROPERTY))
        variable.setValue(value.toString)
        Array(variable)
      }
      case ObjectDebuggerValue(fields, _, _) => {
        fields.flatMap((f) => {
          getVariablesOf(f)
        })
      }
      case ArrayDebuggerValue(values, _, _) => {
        values.zipWithIndex.map((v) => {
          val itemValue = v._1
          val variable = new Variable
          variable.setName("[" + v._2.toString + "]")
          variable.setType(itemValue.typeName())
          setChildNumbers(itemValue, variable)
          setVariableValue(itemValue, variable)
          variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.PROPERTY))
          variable
        })
      }
      case _: SimpleDebuggerValue => {
        Array()
      }
      case KeyDebuggerValue(_, attr, _) => {
        attr.flatMap((attr) => getVariablesOf(attr))
      }
      case df: DebuggerFunction => {
        Array()
      }
    }
  }


  override def source(args: SourceArguments): CompletableFuture[SourceResponse] = {
    logger.log(Level.INFO, "source" + args)
    super.source(args)
  }

  override def threads(): CompletableFuture[ThreadsResponse] = {
    logger.log(Level.INFO, "[DWDebuggerAdapter] threads")

    CompletableFuture.supplyAsync(() => {
      val response = new ThreadsResponse
      val thread = new Thread
      thread.setId(1)
      thread.setName("Main Thread")
      response.setThreads(Array[Thread](thread))
      logger.log(Level.INFO, "[DWDebuggerAdapter] threads Response -> " + response)
      response
    })
  }

  override def terminateThreads(args: TerminateThreadsArguments): CompletableFuture[Void] = {
    logger.log(Level.INFO, "terminateThreads" + args)
    super.terminateThreads(args)
  }

  override def modules(args: ModulesArguments): CompletableFuture[ModulesResponse] = {
    logger.log(Level.INFO, "modules" + args)
    super.modules(args)
  }


  override def loadedSources(args: LoadedSourcesArguments): CompletableFuture[LoadedSourcesResponse] = {
    logger.log(Level.INFO, "loadedSources" + args)
    super.loadedSources(args)
  }

  override def evaluate(args: EvaluateArguments): CompletableFuture[EvaluateResponse] = {
    CompletableFuture.supplyAsync(() => {
      val response = new ObjectHolder[EvaluateResponse]()
      debuggerClient.evaluateScript(args.getFrameId, args.getExpression, new ScriptEvaluationListener() {
        override def onScriptEvaluated(client: DebuggerClient, sr: ScriptResultEvent): Unit = {
          val evalResponse = new EvaluateResponse()
          evalResponse.setResult(sr.result.toString)
          evalResponse.setType(sr.result.typeName())
          variablesRegistry.+=(sr.result)
          evalResponse.setVariablesReference(variablesRegistry.size)
          response.set(evalResponse)
        }
      })
      response.get()
    })
  }
}


class ObjectHolder[T] {

  @volatile
  var result: T = _
  val latch = new CountDownLatch(1)

  def set(v: T): Unit = {
    result = v
    latch.countDown()
  }

  def get(): T = {
    latch.await()
    result
  }
}
