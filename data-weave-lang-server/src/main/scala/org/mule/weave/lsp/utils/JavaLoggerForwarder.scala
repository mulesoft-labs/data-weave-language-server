package org.mule.weave.lsp.utils

import org.mule.weave.lsp.services.ClientLogger

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord

class JavaLoggerForwarder(clientLogger: ClientLogger) extends Handler {
  override def publish(record: LogRecord): Unit = {
    record.getLevel match {
      case Level.INFO => clientLogger.logInfo(record.getMessage)
      case Level.WARNING => clientLogger.logWarning(record.getMessage)
      case Level.SEVERE => {
        if (record.getThrown != null) {
          clientLogger.logError(record.getMessage, record.getThrown)
        }
        else {
          clientLogger.logError(record.getMessage)
        }
      }
      case _ => clientLogger.logInfo(record.getMessage)
    }
  }

  override def flush(): Unit = {
  }

  override def close(): Unit = {
  }

}

object JavaLoggerForwarder {
  def interceptLog[T](clientLogger: ClientLogger)(callback: => T): T = {
    val rootLogger = LogManager.getLogManager.getLogger("")
    val loggerForwarder = new JavaLoggerForwarder(clientLogger)
    rootLogger.addHandler(loggerForwarder)
    try {
      callback
    } finally {
      rootLogger.removeHandler(loggerForwarder)
    }
  }
}


