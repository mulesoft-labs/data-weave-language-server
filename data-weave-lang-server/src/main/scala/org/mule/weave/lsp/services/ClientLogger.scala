package org.mule.weave.lsp.services

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

import java.io.PrintWriter
import java.io.StringWriter

/**
  * Handles the logging to the client
  */
class ClientLogger(_client: LanguageClient) {

  def logDebug(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Log, message))
    }
  }

  def logDebug(message: String, throwable: Throwable): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Log, message + " caused by:\n" + toStringStacktrace(throwable)))
    }
  }

  def logInfo(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Info, message))
    }
  }

  def logInfo(message: String, throwable: Throwable): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Info, message + " caused by:\n" + toStringStacktrace(throwable)))
    }
  }

  def logWarning(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Warning, message))
    }
  }

  def logWarning(message: String, throwable: Throwable): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Warning, message + " caused by:\n" + toStringStacktrace(throwable)))
    }
  }

  def logError(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Error, message))
    }
  }

  def logError(message: String, throwable: Throwable): Unit = {
    if (_client != null) {
      val stackTrace: String = toStringStacktrace(throwable)
      _client.logMessage(new MessageParams(MessageType.Error, message + " caused by:\n" + stackTrace))
    }
  }

  private def toStringStacktrace(throwable: Throwable) = {
    val stringWriter = new StringWriter()
    throwable.printStackTrace(new PrintWriter(stringWriter))
    val stackTrace = stringWriter.toString
    stackTrace
  }
}
