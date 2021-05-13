package org.mule.weave.lsp.project.impl.maven

import org.apache.maven.shared.invoker.InvokerLogger
import org.apache.maven.shared.invoker.InvokerLogger._
import org.mule.weave.lsp.services.ClientLogger

class LSPMavenLogger(clientLogger: ClientLogger) extends InvokerLogger {

  var threshold: Int = 0

  override def debug(message: String): Unit = {
    clientLogger.logDebug(message)
  }

  override def debug(message: String, throwable: Throwable): Unit = {
    clientLogger.logDebug(message, throwable)
  }

  override def info(message: String): Unit = {
    clientLogger.logInfo(message)
  }

  override def info(message: String, throwable: Throwable): Unit = {
    clientLogger.logInfo(message, throwable)
  }

  override def warn(message: String): Unit = {
    clientLogger.logWarning(message)
  }

  override def warn(message: String, throwable: Throwable): Unit = {
    clientLogger.logWarning(message, throwable)
  }

  override def error(message: String): Unit = {
    clientLogger.logError(message)
  }

  override def error(message: String, throwable: Throwable): Unit = {
    clientLogger.logError(message, throwable)
  }

  override def fatalError(message: String): Unit = {
    clientLogger.logError(message)
  }

  override def fatalError(message: String, throwable: Throwable): Unit = {
    clientLogger.logError(message, throwable)
  }

  override def setThreshold(threshold: Int): Unit = {
    this.threshold = threshold
  }

  override def getThreshold: Int = this.threshold

  override def isDebugEnabled: Boolean = threshold >= DEBUG

  override def isErrorEnabled: Boolean = threshold >= ERROR

  override def isFatalErrorEnabled: Boolean = threshold >= FATAL

  override def isInfoEnabled: Boolean = threshold >= INFO

  override def isWarnEnabled: Boolean = threshold >= WARN
}


