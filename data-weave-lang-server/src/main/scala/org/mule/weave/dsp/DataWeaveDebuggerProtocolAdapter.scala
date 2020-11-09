package org.mule.weave.dsp

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

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
import org.mule.weave.lsp.services.MessageLoggerService
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

import scala.collection.mutable.ArrayBuffer

class DataWeaveDebuggerProtocolAdapter(virtualFileSystem: VirtualFileSystem, logger: MessageLoggerService) extends IDebugProtocolServer with DebuggerClientListener {

  private val ROOT_REF = 2147483640

  private var debuggerClient: DebuggerClient = _
  private var protocolClient: IDebugProtocolClient = _

  private var debuggerStatus: OnFrameEvent = _
  private var selectedFrame: DebuggerFrame = _

  private val variablesRegistry = ArrayBuffer[DebuggerValue]()


  def connect(client: IDebugProtocolClient): Unit = {
    protocolClient = client
  }


  override def initialize(args: InitializeRequestArguments): CompletableFuture[Capabilities] = {
    println("[DataWeaveDebuggerProtocolAdapter] initialize: " + args)
    val capabilities = new Capabilities()
    capabilities.setSupportsConditionalBreakpoints(true)
    capabilities.setSupportsStepInTargetsRequest(true)
    capabilities.setSupportsGotoTargetsRequest(false)


    CompletableFuture.completedFuture(capabilities)
  }

  override def configurationDone(args: ConfigurationDoneArguments): CompletableFuture[Void] = {
    println("[DataWeaveDebuggerProtocolAdapter] configurationDone: " + args)
    CompletableFuture.completedFuture(null)
  }


  override def attach(args: util.Map[String, AnyRef]): CompletableFuture[Void] = {
    println("[DataWeaveDebuggerProtocolAdapter] attach: " + args)
    CompletableFuture.supplyAsync(() => {
      debuggerClient = new DebuggerClient(this, TcpClientProtocol(port = TcpClientProtocol.DEFAULT_PORT))
      try {
        debuggerClient.connect().onResponse((ci) => {
          println("Debugger Initialized")
          protocolClient.initialized()
        })
      } catch {
        case e: Exception => {
          val stackTrace = new StringWriter()
          e.printStackTrace(new PrintWriter(stackTrace))
          logger.logError("Unable to connect to client: \n" + stackTrace.toString)
          val exitedEventArguments = new TerminatedEventArguments
          exitedEventArguments.setRestart(true)
          protocolClient.terminated(exitedEventArguments)
        }
      }
      null
    })
  }

  override def launch(args: util.Map[String, AnyRef]): CompletableFuture[Void] = {
    println("[DataWeaveDebuggerProtocolAdapter] launch: " + args)
    CompletableFuture.completedFuture(null)
  }

  override def disconnect(args: DisconnectArguments): CompletableFuture[Void] = {
    println("[DataWeaveDebuggerProtocolAdapter] disconnect: " + args)
    CompletableFuture.supplyAsync(() => {
      debuggerClient.disconnect()
      null
    })
  }

  override def terminate(args: TerminateArguments): CompletableFuture[Void] = {
    println("[DWDebuggerAdapter] terminate: " + args)
    CompletableFuture.completedFuture(null)
  }


  override def onClientInitialized(ci: ClientInitializedEvent): Unit = {
    println("onClientInitialized")
    protocolClient.initialized()
  }

  override def onFrame(client: DebuggerClient, frame: OnFrameEvent): Unit = {
    println("[DWDebuggerAdapter] onFrame: " + frame)
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
    logger.logError("Unexpected error while debugging: " + unexpectedServerErrorEvent.stacktrace)
  }

  override def setBreakpoints(args: SetBreakpointsArguments): CompletableFuture[SetBreakpointsResponse] = {
    println("[DWDebuggerAdapter] setBreakpoints: " + args)
    CompletableFuture.supplyAsync(() => {
      val sourceBreakpoints: Array[SourceBreakpoint] = args.getBreakpoints
      val dwBreakpoints = sourceBreakpoints.flatMap((bp) => {
        val path = new File(args.getSource.getPath)
        val virtualFile = virtualFileSystem.file(path.toURI.toURL.toString)
        if (virtualFile != null) {
          val nameIdentifier = virtualFile.getNameIdentifier.toString()
          println("[DWDebuggerAdapter] Adding breakpoint to " + nameIdentifier + " at " + bp.getLine)
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
    CompletableFuture.supplyAsync(() => {
      debuggerClient.nextStep()
      null
    })
  }

  override def stepIn(args: StepInArguments): CompletableFuture[Void] = {
    CompletableFuture.supplyAsync(() => {
      debuggerClient.stepInto()
      null
    })
  }


  override def setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture[Void] = {
    CompletableFuture.completedFuture(null)
  }

  override def stackTrace(args: StackTraceArguments): CompletableFuture[StackTraceResponse] = {
    println("[DWDebuggerAdapter]  stackTrace" + args)
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
      println("[DWDebuggerAdapter] stackTrace -> Response : " + response)
      response
    })
  }


  override def scopes(args: ScopesArguments): CompletableFuture[ScopesResponse] = {
    println("[DWDebuggerAdapter]  Scopes" + args)
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
      println("[DWDebuggerAdapter] scopes -> Response : " + response)
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


  override def variables(args: VariablesArguments): CompletableFuture[VariablesResponse] = {
    println("[DWDebuggerAdapter] variables : " + args)
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
            println(s"[DWDebuggerAdapter] variable ${args.getVariablesReference} Is not present in registry  : \n-" + variablesRegistry.map(_.typeName()).mkString("\n- "))
            Array()
          }
        }
        response.setVariables(variables)
      }
      println("[DWDebuggerAdapter] variables -> Response :" + response)
      response
    })
  }


  private def setVariableValue(debuggerVariable: DebuggerValue, variable: Variable): Unit = {
    variable.setValue(debuggerVariable.toString)
  }

  private def setChildNumbers(debuggerVariable: DebuggerValue, variable: Variable): Unit = {
    debuggerVariable match {
      case ObjectDebuggerValue(fields, _) => {
        variable.setNamedVariables(fields.length)
      }
      case ArrayDebuggerValue(values, _) => {
        variable.setIndexedVariables(values.length)
      }
      case _ =>
    }

    if(hasChildren(debuggerVariable)){
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
      case FieldDebuggerValue(key, value) => {
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
      case fd@FieldDebuggerValue(key, value) => {
        val variable = new Variable()
        variable.setName(key.name)
        variable.setType(value.typeName())
        setVariableValue(value, variable)
        setChildNumbers(fd, variable)
        variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.DATA))
        Array(variable)
      }
      case AttributeDebuggerValue(name, value) => {
        val variable = new Variable()
        variable.setName("@" + name)
        variable.setPresentationHint(createVariableKind(VariablePresentationHintKind.PROPERTY))
        variable.setValue(value.toString)
        Array(variable)
      }
      case ObjectDebuggerValue(fields, _) => {
        fields.flatMap((f) => {
          getVariablesOf(f)
        })
      }
      case ArrayDebuggerValue(values, _) => {
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
      case SimpleDebuggerValue(value, typeName) => {

        Array()
      }
      case KeyDebuggerValue(name, attr) => {
        attr.flatMap((attr) => getVariablesOf(attr))
      }
      case df: DebuggerFunction => {
        Array()
      }
    }
  }


  override def source(args: SourceArguments): CompletableFuture[SourceResponse] = {
    println("[DWDebuggerAdapter]  source" + args)
    super.source(args)
  }

  override def threads(): CompletableFuture[ThreadsResponse] = {
    println("[DWDebuggerAdapter] threads")

    CompletableFuture.supplyAsync(() => {
      val response = new ThreadsResponse
      val thread = new Thread
      thread.setId(1)
      thread.setName("Main Thread")
      response.setThreads(Array[Thread](thread))
      println("[DWDebuggerAdapter] threads Response -> " + response)
      response
    })
  }

  override def terminateThreads(args: TerminateThreadsArguments): CompletableFuture[Void] = {
    println("[DWDebuggerAdapter]  terminateThreads" + args)
    super.terminateThreads(args)
  }

  override def modules(args: ModulesArguments): CompletableFuture[ModulesResponse] = {
    println("[DWDebuggerAdapter]  modules" + args)
    super.modules(args)
  }


  override def loadedSources(args: LoadedSourcesArguments): CompletableFuture[LoadedSourcesResponse] = {
    println("[DWDebuggerAdapter]  loadedSources" + args)
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